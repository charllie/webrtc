package cz.cvut.fel.webrtc.handlers;

import gov.nist.javax.sip.header.CallID;

import java.io.IOException;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import javax.sdp.SdpFactory;
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

import cz.cvut.fel.webrtc.db.RoomManager;
import cz.cvut.fel.webrtc.db.SipRegistry;
import cz.cvut.fel.webrtc.resources.Line;
import cz.cvut.fel.webrtc.resources.Room;
import cz.cvut.fel.webrtc.resources.Softphone;

public class SipHandler extends TextWebSocketHandler {
	
	@Autowired
	private RoomManager roomManager;
	
	@Autowired
	private SipRegistry sipRegistry;

	protected Logger log = LoggerFactory.getLogger(SipHandler.class);

	private SipFactory sipFactory;
	private SdpFactory sdpFactory;
	private MessageFactory messageFactory;
	private HeaderFactory headerFactory;
	private AddressFactory addressFactory;
	
	private String ip;
	// TODO
	private String asteriskIp = "178.62.211.128";
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
			
			sdpFactory = SdpFactory.getInstance();
			
		
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
		timer.scheduleAtFixedRate(task, 0, 20000);
		
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
		case 401:
			try {
				CSeqHeader cSeqHeader = (CSeqHeader) response.getHeader("CSeq");
				String method = cSeqHeader.getMethod();
				FromHeader fromHeader = (FromHeader) response.getHeader("From");
				Room room = roomManager.getRoom(fromHeader.getAddress().getDisplayName());

				if (room != null) {
					if (method.equals(Request.REGISTER))
						register(room, response);
					if (method.equals(Request.INVITE)) {
						generateInviteRequest(room, response);
					}
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
	
	public void register(Room room, Response response) throws Exception {
		Line account = room.getLine();
		
		if (account == null)
			account = sipRegistry.popLine(room);
		
		if (account != null) {
			String username = account.getUsername();
			String password = account.getSecret();
			String sipAddress = String.format("sip:%s@%s", username, asteriskIp);
			
			sipRegistry.addRoomByURI(sipAddress, room.getName());
			
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
			contactHeader.setExpires(200);
			
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
				AuthorizationHeader authorization = headerFactory.createAuthorizationHeader(authHeader.getScheme());
				
				String authResponse = calculateResponse(authHeader, Request.REGISTER, sipAddress, username, password);
		
				if (authResponse != null) {
					authorization.setUsername(username);
					authorization.setRealm(authHeader.getRealm());
					authorization.setNonce(authHeader.getNonce());
					authorization.setURI(requestURI);	
					authorization.setResponse(authResponse);
					
					request.addHeader(authorization);
				} else {
					return;
				}
			}
			
			sendMessage(request);
		}
	}
	
	public void generateInviteRequest(Room room, String callee) {
		
		try {
			
			String sipAddress = String.format("sip:%s@%s", callee, asteriskIp);
			Address address = addressFactory.createAddress(sipAddress);
			address.setDisplayName(callee);
			ToHeader toHeader = headerFactory.createToHeader(address, null);

			final Softphone user = (Softphone) room.join(callee, session, Softphone.class);
			
			if (user != null)
				generateInviteRequest(room, user, toHeader, null);
		
		} catch (Exception e) {
			log.info("Cannot create INVITE request: {}", e);
		}
	}
	
	public void generateInviteRequest(Room room, Response response) {
		
		try {
			
			ToHeader toHeader = (ToHeader) response.getHeader("To");
			String callee = toHeader.getAddress().getDisplayName();
			final Softphone user = (Softphone) room.getParticipant(callee);
			
			if (user != null)
				generateInviteRequest(room, user, toHeader, response);
			
		} catch (Exception e) {
			log.info("Cannot create INVITE request: {}", e);
		}
		
	}
	
	@Async
	public void generateInviteRequest(Room room, Softphone user, ToHeader toHeader, Response response) throws ParseException, InvalidArgumentException, NoSuchAlgorithmException {
		Line account = room.getLine();
		
		if (account == null)
			return;
			
		if (user == null)
			return;
		
		String sdpOffer = user.getGeneratedOffer();
		
		String username = account.getUsername();
		String password = account.getSecret();
		String sipAddressString = String.format("sip:%s@%s", username, asteriskIp);

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
			AuthorizationHeader authorization = headerFactory.createAuthorizationHeader(authHeader.getScheme());
			
			String authResponse = calculateResponse(authHeader, Request.INVITE, requestURI.toString(), username, password);
	
			if (authResponse != null) {
				authorization.setUsername(username);
				authorization.setRealm(authHeader.getRealm());
				authorization.setNonce(authHeader.getNonce());
				authorization.setURI(requestURI);	
				authorization.setResponse(authResponse);
				
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
			
			Room room = sipRegistry.getRoomBySipURI(uri);
			
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
			String username = sender.getDisplayName();
			
			if (username == null)
				username = sender.getURI().toString();
			
			Softphone user = (Softphone) room.join(username, session, Softphone.class);
			
			if (user == null)
				return;
			
			String sdpAnswer = user.getSdpAnswer(sdpOffer);
			
			// 200 OK
			ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
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
		
		Room room = sipRegistry.getRoomBySipURI(uri);
		
		String username = sender.getDisplayName();
		
		if (username == null)
			username = sender.getURI().toString();
		
		if (room != null) {
			try {
				
				room.leave(username);
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
	
	private static String MD5(String toBeHashed) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("MD5");
		md.update(toBeHashed.getBytes());
		
		byte byteData[] = md.digest();
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < byteData.length; i++)
			sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
		
		return sb.toString();
	}
	
	private static String calculateResponse(WWWAuthenticateHeader authHeader, String method, String uri, String username, String password) throws NoSuchAlgorithmException {
		String realm = authHeader.getRealm();
		String qop = authHeader.getQop();
		String nonce = authHeader.getNonce();
		
		String ha1 = MD5(String.format("%s:%s:%s", username, realm, password));
		String ha2;
		String response = null;
		
		if (qop == null) {
			ha2 = MD5(String.format("%s:%s", method, uri));
			response = MD5(String.format("%s:%s:%s", ha1, nonce, ha2));
		} else if (qop.equals("auth")) {
			// TODO
		} else if (qop.equals("auth-int")) {
			// TODO
		}
		
		return response;
	}
}