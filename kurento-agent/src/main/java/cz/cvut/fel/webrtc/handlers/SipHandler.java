package cz.cvut.fel.webrtc.handlers;

import gov.nist.javax.sip.header.CallID;

import java.io.IOException;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Random;

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
import cz.cvut.fel.webrtc.db.SipRegistry.Account;
import cz.cvut.fel.webrtc.resources.Room;
import cz.cvut.fel.webrtc.resources.SoftUserSession;

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
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		this.session = session;
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
				
				ToHeader toHeader = (ToHeader)response.getHeader("To");
				Room room = sipRegistry.getRoomBySipURI(toHeader.getAddress().getURI().toString());
				
				if (room != null)
					register(room, response);
				
			} catch (Exception e) {}
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
		Account account = room.getAccount();
		
		if (account == null)
			account = sipRegistry.getFor(room);
		
		if (account != null) {
			String username = account.getUsername();
			String password = account.getPassword();
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
			
			SoftUserSession user = (SoftUserSession) room.join(username, session, SoftUserSession.class);
			
			if (user == null)
				return;
			
			String sdpAnswer = user.getSdpAnswer(sdpOffer);
			
			/*for (String candidate : user.getCandidates())
				sdpAnswer = sdpAnswer.concat("\r\na=" + candidate);
			sdpAnswer = sdpAnswer.concat("\r\n");*/
			
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