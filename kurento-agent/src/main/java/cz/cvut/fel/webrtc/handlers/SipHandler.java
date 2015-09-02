package cz.cvut.fel.webrtc.handlers;

import gov.nist.javax.sip.header.CallID;

import java.io.IOException;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Random;
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
import cz.cvut.fel.webrtc.utils.Digest;

public class SipHandler extends TextWebSocketHandler {
	
	@Autowired
	private RoomManager roomManager;
	
	@Autowired
	private LineRegistry lineRegistry;

	protected Logger log = LoggerFactory.getLogger(SipHandler.class);

	private SipFactory sipFactory;
	private MessageFactory messageFactory;
	private HeaderFactory headerFactory;
	private AddressFactory addressFactory;
	
	private String ip;
	// TODO
	private String pbxIp = "178.62.211.128";

	// TODO
	private int port = 8080;
	private String protocol = "ws";
	private int tag = (new Random()).nextInt();
	private WebSocketSession session = null;
	
	public SipHandler() {
		
		try {
			
			// Configure the SIP listener
			sipFactory = SipFactory.getInstance();
			sipFactory.setPathName("gov.nist");
				
			messageFactory = sipFactory.createMessageFactory();
			addressFactory = sipFactory.createAddressFactory();
			headerFactory = sipFactory.createHeaderFactory();
			
			ip = InetAddress.getLocalHost().getHostAddress();

		} catch (Exception e) {
		
			log.error("{}", e);
			System.exit(-1);
		
		}
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
			sipMessage = messageFactory.createRequest(payload);
			sipClass = sipMessage.getClass();
		} catch (Exception e) {}
		
		// Response
		if (sipClass == null) { 
			try {
				sipMessage = messageFactory.createResponse(payload);
				sipClass = sipMessage.getClass();
			} catch (Exception e) {}
		}
		
		// Use the right method (asynchronously)
		if (Request.class.isAssignableFrom(sipClass)) {
			log.info("Received a SIP Request Message \n{}", payload);
			processRequest((Request)sipMessage);
		} else if (Response.class.isAssignableFrom(sipClass)) {
			processResponse((Response)sipMessage);
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
		
		// Send Ack
		long cseq = ((CSeqHeader) response.getHeader("CSeq")).getSeqNumber();
		CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq, Request.ACK);
		MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);
		
		ViaHeader viaHeaderCallee = (ViaHeader) response.getHeader("Via");
		ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
		ViaHeader viaHeader = headerFactory.createViaHeader(ip, port, protocol, null);
		viaHeaders.add(viaHeader);
		viaHeaders.add(viaHeaderCallee);
		
		Request request = messageFactory.createRequest(
				toHeader.getAddress().getURI(),
				Request.ACK,
				(CallIdHeader) response.getHeader("Call-ID"),
				cSeqHeader,
				(FromHeader) response.getHeader("From"),
				toHeader,
				viaHeaders,
				maxForwardsHeader
		);
		
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
			
			// Address
			Address address = addressFactory.createAddress(sipAddress);
			address.setDisplayName(room.getName());
			
			// URI
			URI requestURI = address.getURI();
			
			// Via
			ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
			ViaHeader viaHeader = headerFactory.createViaHeader(ip, port, protocol, null);
			viaHeaders.add(viaHeader);
			
			// Max-Forwards
			MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);
			
			// Contact
			ContactHeader contactHeader = headerFactory.createContactHeader(address);
			contactHeader.setExpires(expire);
			
			// Call Id
			CallIdHeader callIdHeader = new CallID(room.getCallId());
			
			// CSeq
			long cseq = (response == null) ? room.getCSeq() : room.setCSeq(((CSeqHeader)response.getHeader("CSeq")).getSeqNumber() + 1);
			CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq, Request.REGISTER);
			
			// From / To
			FromHeader fromHeader = headerFactory.createFromHeader(address, String.valueOf(tag));
			ToHeader toHeader = headerFactory.createToHeader(address, null);
			
			// Build the request
			Request request = messageFactory.createRequest(
					requestURI,
					Request.REGISTER,
					callIdHeader,
					cSeqHeader,
					fromHeader,
					toHeader,
					viaHeaders,
					maxForwardsHeader
			);
			request.addHeader(contactHeader);
			
			if (response != null) {
				
				WWWAuthenticateHeader authHeader = (WWWAuthenticateHeader)response.getHeader("WWW-Authenticate");
				String strAuthHeader = authHeader.toString().replace("WWW-Authenticate: ", "");
				String authResponse = Digest.getHeaderResponse(Request.REGISTER, requestURI.toString(), strAuthHeader, username, password);
				
				if (authResponse != null) {
					AuthorizationHeader authorization = headerFactory.createAuthorizationHeader(authResponse);
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

			String sipAddress = String.format("sip:%s@%s", extension, pbxIp);
			
			Address address = addressFactory.createAddress(sipAddress);
			address.setDisplayName(extension);
			ToHeader toHeader = headerFactory.createToHeader(address, null);

			final Softphone user = (Softphone) room.join(sipAddress, session, Softphone.class);
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
		
		if (line == null)
			return;
			
		if (user == null)
			return;
		
		String sdpOffer = user.getGeneratedOffer();
		
		String username = line.getUsername();
		String password = line.getSecret();
		String sipAddressString = String.format("sip:%s@%s", username, pbxIp);

		Address sipAddress = addressFactory.createAddress(sipAddressString);
		sipAddress.setDisplayName(room.getName());

		URI requestURI = toHeader.getAddress().getURI();

		ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
		ViaHeader viaHeader = headerFactory.createViaHeader(ip, port, protocol, null);
		viaHeaders.add(viaHeader);

		MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);

		ContactHeader contactHeader = headerFactory.createContactHeader(toHeader.getAddress());
		contactHeader.setExpires(200);

		CallIdHeader callIdHeader = new CallID(room.getCallId());

		long cseq = (response == null) ? room.setCSeq(room.getCSeq() + 1) : room.setCSeq(((CSeqHeader)response.getHeader("CSeq")).getSeqNumber() + 1);
		CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq, Request.INVITE);

		FromHeader fromHeader = headerFactory.createFromHeader(sipAddress, String.valueOf(tag));
		
		ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
		
		// Build the request
		Request request = messageFactory.createRequest(
				requestURI,
				Request.INVITE,
				callIdHeader,
				cSeqHeader,
				fromHeader,
				toHeader,
				viaHeaders,
				maxForwardsHeader,
				contentTypeHeader,
				sdpOffer
				
		);
		request.addHeader(contactHeader);

		if (response != null) {
			
			WWWAuthenticateHeader authHeader = (WWWAuthenticateHeader)response.getHeader("WWW-Authenticate");
			String strAuthHeader = authHeader.toString().replace("WWW-Authenticate: ", "");
			String authResponse = Digest.getHeaderResponse(Request.INVITE, requestURI.toString(), strAuthHeader, username, password);
			
			if (authResponse != null) {
				AuthorizationHeader authorization = headerFactory.createAuthorizationHeader(authResponse);
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
			
			FromHeader fromHeader = headerFactory.createFromHeader(receiver, String.valueOf(tag));
			ToHeader toHeader = headerFactory.createToHeader(sender, null);
			CallIdHeader callIdHeader = (CallIdHeader) request.getHeader("Call-ID");
			CSeqHeader cSeqHeader = (CSeqHeader) request.getHeader("CSeq");
			ViaHeader viaHeaderSender = (ViaHeader) request.getHeader("Via");
			
			ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
			ViaHeader viaHeader = headerFactory.createViaHeader(ip, port, protocol, null);
			viaHeaders.add(viaHeader);
			viaHeaders.add(viaHeaderSender);
			
			MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);
			
			// Trying
			Response tryingResponse = messageFactory.createResponse(
					100,
					callIdHeader,
					cSeqHeader,
					fromHeader,
					toHeader,
					viaHeaders,
					maxForwardsHeader
			);
			sendMessage(tryingResponse);
			
			// Ringing
			Response ringingResponse = messageFactory.createResponse(
					180,
					callIdHeader,
					cSeqHeader,
					fromHeader,
					toHeader,
					viaHeaders,
					maxForwardsHeader
			);
			sendMessage(ringingResponse);
			
			// Create a new user
			String userId = sender.getURI().toString();
			
			Softphone user = (Softphone) room.join(userId, session, Softphone.class);
			
			if (user == null)
				return;

			String name = sender.getDisplayName();
			if (name == null)
				name = sender.getURI().toString();
			
			user.setName(name);
			
			String sdpAnswer = user.getSdpAnswer(sdpOffer);
			
			// 200 OK
			ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
			room.joinRoom(user);
			Response okResponse = messageFactory.createResponse(
					200,
					callIdHeader,
					cSeqHeader,
					fromHeader,
					toHeader,
					viaHeaders,
					maxForwardsHeader,
					contentTypeHeader,
					sdpAnswer
			);
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
				FromHeader fromHeader = headerFactory.createFromHeader(receiver, String.valueOf(tag));
			
				ToHeader toHeader = headerFactory.createToHeader(sender, null);
				CallIdHeader callIdHeader = (CallIdHeader) request.getHeader("Call-ID");
				CSeqHeader cSeqHeader = (CSeqHeader) request.getHeader("CSeq");
				ViaHeader viaHeaderSender = (ViaHeader) request.getHeader("Via");
				
				ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
				ViaHeader viaHeader = headerFactory.createViaHeader(ip, port, protocol, null);
				viaHeaders.add(viaHeader);
				viaHeaders.add(viaHeaderSender);
				
				MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);
				
				// Trying
				Response tryingResponse = messageFactory.createResponse(
						200,
						callIdHeader,
						cSeqHeader,
						fromHeader,
						toHeader,
						viaHeaders,
						maxForwardsHeader
				);
				sendMessage(tryingResponse);
				
			} catch (Exception e) {}
		}
	}

	public String getPbxIp() {
		return pbxIp;
	}
}