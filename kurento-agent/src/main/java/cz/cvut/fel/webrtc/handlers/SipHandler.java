package cz.cvut.fel.webrtc.handlers;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Timer;
import java.util.TimerTask;

import javax.sip.*;
import javax.sip.address.*;
import javax.sip.header.*;
import javax.sip.message.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.JsonObject;

import cz.cvut.fel.webrtc.db.RoomManager;
import cz.cvut.fel.webrtc.db.LineRegistry;
import cz.cvut.fel.webrtc.resources.Line;
import cz.cvut.fel.webrtc.resources.Room;
import cz.cvut.fel.webrtc.resources.Softphone;
import cz.cvut.fel.webrtc.utils.DigestAuth;
import cz.cvut.fel.webrtc.utils.SipMessageFactory;

public class SipHandler extends TextWebSocketHandler {
	
	@Autowired
	private RoomManager roomManager;
	
	@Autowired
	private LineRegistry lineRegistry;

	protected Logger log = LoggerFactory.getLogger(SipHandler.class);

	private final SipMessageFactory sipFactory;

	private final String pbxIp;

	private WebSocketSession session;
	
	public SipHandler(String pbxIp) {
		
		this.pbxIp = pbxIp;
		this.sipFactory = new SipMessageFactory();
		
	}
	
	@Override
	public void afterConnectionEstablished(final WebSocketSession session) throws Exception {
			this.session = session;
			TimerTask task = new TimerTask() {

				@Override
				public void run() {
					try {
						log.info("Ping on PBX to keep the websocket alive");
						session.sendMessage(new TextMessage("stay-alive"));
					} catch (IOException e) {
						log.info("Ping on PBX failed");
					}
				}

			};
			
			Timer timer = new Timer();
			timer.scheduleAtFixedRate(task, 0, 30000);
	}

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		
		String payload = message.getPayload();
		Class<? extends Message> sipClass = null;
		Message sipMessage = null;
		
		// Request
		try {
			
			sipMessage = sipFactory.createRequest(payload);
			sipClass = sipMessage.getClass();
		
		} catch (Exception e) {}
		
		// Response
		if (sipClass == null) { 
		
			try {
				
				sipMessage = sipFactory.createResponse(payload);
				sipClass = sipMessage.getClass();
				
			} catch (Exception e) {}

		}
		
		// Use the right method (asynchronously)
		if (Request.class.isAssignableFrom(sipClass)) {
			
			log.info("Received a SIP Request Message \n{}", payload);
			processRequest((Request) sipMessage);
			
		} else if (Response.class.isAssignableFrom(sipClass)) {

			processResponse((Response) sipMessage);
			log.info("Received a SIP Response Message \n{}", payload);

		} else {

			log.info("Received an unhandled message \n{}", payload);

		}
	}
	
	@Async
	private void processResponse(Response response) {
		
		switch(response.getStatusCode()) {
		case 200:
			try {
				CSeqHeader cSeqHeader = (CSeqHeader) response.getHeader("CSeq");
				String method = cSeqHeader.getMethod();
				FromHeader fromHeader = (FromHeader) response.getHeader("From");
				Room room = roomManager.getRoom(fromHeader.getAddress().getDisplayName());

				switch(method) {

				case Request.INVITE:
					processInviteResponse(room, response);
					break;

				case Request.REGISTER:
					if (room.isClosing()) {
						roomManager.removeRoom(room);
					} else if (room.getLine() != null) {
						final JsonObject message = new JsonObject();
						message.addProperty("id", "lineAvailable");
						message.addProperty("extension", room.getLine().getExtension());
						room.broadcast(message);
					}
					break;

				default:
					break;

				}
					
			} catch (Exception e) {
				log.info("Cannot process a 200 response {}", e);
			}
			break;

		case 401:
			try {
				CSeqHeader cSeqHeader = (CSeqHeader) response.getHeader("CSeq");
				String method = cSeqHeader.getMethod();
				FromHeader fromHeader = (FromHeader) response.getHeader("From");
				Room room = roomManager.getRoom(fromHeader.getAddress().getDisplayName());

				switch(method) {
				
				case Request.INVITE:
					generateInviteRequest(room, response);
					break;
				
				case Request.REGISTER:
					register(room, response);
					break;
					
				default:
					break;
				}
				
			} catch (Exception e) {
				log.info("Cannot process a 401 response {}", e);
			}
			break;

		default:
			break;
		}
		
	}

	@Async
	private void processRequest(Request request) {
		
		switch(request.getMethod()) {
		case Request.INVITE:
			processInviteRequest(request);
			break;

		case Request.BYE:
			processByeRequest(request);
			break;
			
		default:
			break;
		}
		
	}
	
	public void sendMessage(Message message) {
		try {
			synchronized (session) {
				String textMessage = message.toString();
				log.info("Sending message \n{}", textMessage);
				session.sendMessage(new TextMessage(textMessage));
			}
		} catch (IOException e) {
			log.debug(e.getMessage());
		}
	}
	
	private void processInviteResponse(Room room, Response response) throws IOException, ParseException, InvalidArgumentException {
		
		// Process SDP Answer
		String sdpAnswer = (String) response.getContent();
		ToHeader toHeader = (ToHeader) response.getHeader("To");
		
		Softphone callee = (Softphone) room.getParticipant(toHeader.getAddress().getURI().toString());
		callee.getRtpEndpoint().processAnswer(sdpAnswer);
		room.joinRoom(callee);
		
		Request request = sipFactory.createRequest(response, Request.ACK);
		sendMessage(request);
	}
	
	public void unregister(Room room) throws Exception {
		room.setClosing();
		register(room, null);
	}
	
	public void register(Room room, Response response) throws Exception {
		Line line = room.getLine();
		
		int expire = (room.isClosing()) ? 0 : 604800;
		
		if (line == null)
			line = lineRegistry.popLine(room);
		
		if (line != null) {
			String username = line.getUsername();
			String password = line.getSecret();
			String sipAddress = String.format("sip:%s@%s", username, pbxIp);
			
			lineRegistry.addRoomByURI(sipAddress, room.getName());
			
			long cseq = (response == null) ? room.getCSeq() : room.setCSeq(((CSeqHeader)response.getHeader("CSeq")).getSeqNumber() + 1);
			Request request = sipFactory.createRequest(Request.REGISTER, room.getName(), sipAddress, sipAddress, room.getCallId(), cseq, expire);
			
			if (response != null) {
				
				WWWAuthenticateHeader authHeader = (WWWAuthenticateHeader) response.getHeader("WWW-Authenticate");
				String strAuthHeader = authHeader.toString().replace("WWW-Authenticate: ", "");
				String authResponse = DigestAuth.getHeaderResponse(Request.REGISTER, sipAddress, strAuthHeader, username, password);
				
				if (authResponse != null) {
					AuthorizationHeader authorization = sipFactory.createAuthorizationHeader(authResponse);
					request.addHeader(authorization);
				} else {
					return;
				}
			}
			
			sendMessage(request);
		}
	}
	
	public void generateInviteRequest(Room room, String extension) {
		
		try {

			String address = String.format("sip:%s@%s", extension, pbxIp);
			ToHeader toHeader = sipFactory.createToHeader(address, extension);

			final Softphone user = (Softphone) room.join(address, session, Softphone.class);
			user.setName(extension);
			
			// Find a more appropriate name
			getName(user, extension);
			
			if (user != null)
				generateInviteRequest(room, user, toHeader, null);
		
		} catch (Exception e) {
			log.info("Cannot create INVITE request: {}", e);
		}
	}
	
	@Async
	private void getName(Softphone user, String extension) {
		String name = lineRegistry.getName(extension);
		
		if (name != null)
			user.setName(name);
	}
	
	public void generateInviteRequest(Room room, Response response) {
		
		try {
			
			ToHeader toHeader = (ToHeader) response.getHeader("To");
			String callee = toHeader.getAddress().getURI().toString();
			final Softphone user = (Softphone) room.getParticipant(callee);
			
			if (user != null)
				generateInviteRequest(room, user, toHeader, response);
			
		} catch (Exception e) {
			log.info("Cannot create INVITE request: {}", e);
		}
		
	}
	
	@Async
	public void generateInviteRequest(Room room, Softphone user, ToHeader toHeader, Response response) throws ParseException, InvalidArgumentException, NoSuchAlgorithmException, Exception {
		Line line = room.getLine();
		
		if ((line == null) || (user == null))
			return;
		
		String username = line.getUsername();
		String password = line.getSecret();
		String method = Request.INVITE;
		String callId = room.getCallId();
		String sip = String.format("sip:%s@%s", username, pbxIp);
		
		long cseq = (response == null) ? room.setCSeq(room.getCSeq() + 1) : room.setCSeq(((CSeqHeader)response.getHeader("CSeq")).getSeqNumber() + 1);
		FromHeader from = sipFactory.createFromHeader(sip, room.getName());
		
		Request request = sipFactory.createRequest(method, from, toHeader, callId, cseq, 200);
		ContentTypeHeader contentTypeHeader = sipFactory.createContentTypeHeader("application", "sdp");
		
		String sdpOffer = user.getGeneratedOffer();
		request.setContent(sdpOffer, contentTypeHeader);

		if (response != null) {
			
			WWWAuthenticateHeader authHeader = (WWWAuthenticateHeader)response.getHeader("WWW-Authenticate");
			String strAuthHeader = authHeader.toString().replace("WWW-Authenticate: ", "");
			String authResponse = DigestAuth.getHeaderResponse(method, toHeader.getAddress().getURI().toString(), strAuthHeader, username, password);
			
			if (authResponse != null) {
				AuthorizationHeader authorization = sipFactory.createAuthorizationHeader(authResponse);
				request.addHeader(authorization);
			} else {
				return;
			}
		}

		sendMessage(request);
	}
	
	@Async
	private void processInviteRequest(Request request) {
		try {
			Address sender = ((FromHeader) request.getHeader("From")).getAddress();
			Address receiver = ((ToHeader) request.getHeader("To")).getAddress();
			String uri = receiver.getURI().toString();
			
			String sdpOffer = request.getContent().toString();
			
			Room room = lineRegistry.getRoomBySipURI(uri);
			
			// Trying
			Response tryingResponse = sipFactory.createResponseFromRequest(request, 100);
			sendMessage(tryingResponse);
			
			// Ringing
			Response ringingResponse = sipFactory.cloneResponseWithAnotherStatusCode(tryingResponse, 180);
			sendMessage(ringingResponse);
			
			// Create a new user
			String userId = sender.getURI().toString();
			Softphone user = (Softphone) room.join(userId, session, Softphone.class);
			String name = sender.getDisplayName();
			
			if (name == null)
				name = sender.getURI().toString();
			
			if (user == null)
				return;

			String sdpAnswer = user.getSdpAnswer(sdpOffer);
			user.setName(name);
			room.joinRoom(user);
			
			// 200 OK
			ContentTypeHeader contentTypeHeader = sipFactory.createContentTypeHeader("application", "sdp");
			Response okResponse = sipFactory.cloneResponseWithAnotherStatusCode(ringingResponse, 200);
			okResponse.setContent(sdpAnswer, contentTypeHeader);
			sendMessage(okResponse);
			
		
		} catch (Exception e) {
			log.info("Cannot process to invite request: {}", e);
		}
	}
	
	@Async
	private void processByeRequest(Request request) {
		
		Address sender = ((FromHeader) request.getHeader("From")).getAddress();
		Address receiver = ((ToHeader) request.getHeader("To")).getAddress();
		String uri = receiver.getURI().toString();
		Room room = lineRegistry.getRoomBySipURI(uri);
		
		String userId = sender.getURI().toString();
		
		if (room != null) {
			try {
				
				room.leave(userId);
				Response response = sipFactory.createResponseFromRequest(request, 200);
				sendMessage(response);
				
			} catch (Exception e) {}
		}
	}

	public String getPbxIp() {
		return pbxIp;
	}
}