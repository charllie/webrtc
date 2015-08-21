package cz.cvut.fel.webrtc.handlers;

import gov.nist.javax.sip.header.CallID;

import java.io.IOException;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Random;

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

import cz.cvut.fel.webrtc.db.SipRegistry;

public class SipHandler extends TextWebSocketHandler {
	
	@Autowired
	private SipRegistry sipRegistry;

	protected Logger log = LoggerFactory.getLogger(SipHandler.class);

	private SipFactory sipFactory;
	private MessageFactory messageFactory;
	private HeaderFactory headerFactory;
	private AddressFactory addressFactory;
	
	private String ip;
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
	
	public Request register(long cseq) throws Exception {
		String sipAddress = "sip:ntmudo@178.62.211.128";

		// Get the destination address from the text field.
		Address address = addressFactory.createAddress(sipAddress);
		address.setDisplayName("Toto");
		URI requestURI = address.getURI();
		ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
		ViaHeader viaHeader = headerFactory.createViaHeader(ip, port, protocol, null);
		viaHeaders.add(viaHeader);
		MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);
		ContactHeader contactHeader = headerFactory.createContactHeader(address);
		contactHeader.setExpires(200);
		
		
		// TODO
		CallIdHeader callIdHeader = new CallID("testons");
		CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq,"REGISTER");
		FromHeader fromHeader = headerFactory.createFromHeader(address, String.valueOf(tag));
		ToHeader toHeader = headerFactory.createToHeader(address, null);
		Request request = messageFactory.createRequest(
				requestURI,
				"REGISTER",
				callIdHeader,
				cSeqHeader,
				fromHeader,
				toHeader,
				viaHeaders,
				maxForwardsHeader
		);
		
		request.addHeader(contactHeader);
		
		return request;
		
	}
	
	@Async
	private void processResponse(Response response) {
		
		switch(response.getStatusCode()) {
		case 401:
			try {
				WWWAuthenticateHeader authHeader = (WWWAuthenticateHeader)response.getHeader("WWW-Authenticate");
				AuthorizationHeader authorization = headerFactory.createAuthorizationHeader(authHeader.getScheme());
				
				String username = "ntmudo";
				String password = "NP5DAB";
				String uri = "sip:178.62.211.128";
				
				String authResponse = calculateResponse(authHeader, Request.REGISTER, uri, username, password);

				if (authResponse != null) {
					authorization.setUsername(username);
					authorization.setRealm(authHeader.getRealm());
					authorization.setNonce(authHeader.getNonce());
					authorization.setURI(addressFactory.createAddress(uri).getURI());	
					authorization.setResponse(authResponse);
					
					long cseq = ((CSeqHeader)response.getHeader("CSeq")).getSeqNumber() + 1;
					Request request = register(cseq);
					request.addHeader(authorization);
					sendMessage(request);
					
				}
				
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
			
		default:
			break;
		}
		
	}
	
	private void processInviteRequest(Request request) {
		FromHeader fromHeader = (FromHeader) request.getHeader("From");
		Address address = fromHeader.getAddress();
		
		String username = address.getDisplayName();
		
		if (username == null)
			username = address.getURI().toString();
		
		// TODO
	}
	
	private String MD5(String toBeHashed) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("MD5");
		md.update(toBeHashed.getBytes());
		
		byte byteData[] = md.digest();
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < byteData.length; i++)
			sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
		
		return sb.toString();
	}
	
	private String calculateResponse(WWWAuthenticateHeader authHeader, String method, String uri, String username, String password) throws NoSuchAlgorithmException {
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
	
	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		this.session = session;

		// PING TODO
		
		// Test REGISTER
		Request request = register(1L);
		sendMessage(request);
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

}