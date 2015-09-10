package cz.cvut.fel.webrtc.utils;

import gov.nist.javax.sip.header.CallID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.InvalidArgumentException;
import javax.sip.SipFactory;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.net.InetAddress;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Random;

public class SipMessageFactory {
	
	private final Logger log = LoggerFactory.getLogger(SipMessageFactory.class);

	private MessageFactory messageFactory;
	private HeaderFactory headerFactory;
	private AddressFactory addressFactory;
	private String ip;
	// TODO
	private final int port = 8080;
	private final String protocol = "ws";
	private final int tag = (new Random()).nextInt();

	public SipMessageFactory() {
		try {

			SipFactory sipFactory = SipFactory.getInstance();
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
	
	public Response createResponseFromRequest(Request request, int statusCode) throws ParseException, InvalidArgumentException {
		Address sender = ((FromHeader) request.getHeader("From")).getAddress();
		Address receiver = ((ToHeader) request.getHeader("To")).getAddress();

		FromHeader fromHeader = headerFactory.createFromHeader(receiver, String.valueOf(tag));
		ToHeader toHeader = headerFactory.createToHeader(sender, null);
		CallIdHeader callIdHeader = (CallIdHeader) request.getHeader("Call-ID");
		CSeqHeader cSeqHeader = (CSeqHeader) request.getHeader("CSeq");
		ViaHeader viaHeaderSender = (ViaHeader) request.getHeader("Via");
		
		ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
		ViaHeader viaHeader = headerFactory.createViaHeader(ip, port, protocol, null);
		viaHeaders.add(viaHeader);

		if (viaHeaderSender != null)
			viaHeaders.add(viaHeaderSender);

		MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);

		return messageFactory.createResponse(
				statusCode,
				callIdHeader,
				cSeqHeader,
				fromHeader,
				toHeader,
				viaHeaders,
				maxForwardsHeader
		);
	}
	
	public Response cloneResponseWithAnotherStatusCode(Response response, int statusCode) throws ParseException {
		Response clone = (Response) response.clone();
		clone.setStatusCode(statusCode);
		return clone;
	}
	
	public Request createRequest(Response response, String method) throws ParseException, InvalidArgumentException {
		FromHeader from = (FromHeader) response.getHeader("From");
		ToHeader to = (ToHeader) response.getHeader("To");
		String callId = ((CallIdHeader) response.getHeader("Call-ID")).getCallId();
		long cseq = ((CSeqHeader) response.getHeader("CSeq")).getSeqNumber();
		
		return createRequest(method, from, to, callId, cseq, -1);
	}
	
	public Request createRequest(String method, String displayName, String from, String to, String callId, long cseq, int expire) throws ParseException, InvalidArgumentException {
		
		Address toAddress = addressFactory.createAddress(to);
		toAddress.setDisplayName(displayName);
		
		Address fromAddress = (from.equals(to)) ? toAddress : addressFactory.createAddress(from);
		
		FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, String.valueOf(tag));
		ToHeader toHeader = headerFactory.createToHeader(toAddress, null);
		
		return createRequest(method, fromHeader, toHeader, callId, cseq, expire);		
	}
	
	public Request createRequest(String method, FromHeader from, ToHeader to, String callId, long cseq, int expire) throws ParseException, InvalidArgumentException {

		Address toAddress = to.getAddress();
		URI uri = toAddress.getURI();
		
		ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
		ViaHeader viaHeader = headerFactory.createViaHeader(ip, port, protocol, null);
		viaHeaders.add(viaHeader);
		
		MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);
		
		CallIdHeader callIdHeader = new CallID(callId);
		CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(cseq, method);
		
		Request request = messageFactory.createRequest(
				uri,
				method,
				callIdHeader,
				cSeqHeader,
				from,
				to,
				viaHeaders,
				maxForwardsHeader
		);
		
		if (expire >= 0) {
			ContactHeader contactHeader = headerFactory.createContactHeader(toAddress);
			contactHeader.setExpires(expire);
			request.addHeader(contactHeader);
		}
		
		return request;
	}
	
	public Request createRequest(String request) throws ParseException {
		return messageFactory.createRequest(request);
	}

	public Response createResponse(String response) throws ParseException {
		return messageFactory.createResponse(response);
	}
	
	public ContentTypeHeader createContentTypeHeader(String type, String specific) throws ParseException {
		return headerFactory.createContentTypeHeader(type, specific);
	}
	
	public AuthorizationHeader createAuthorizationHeader(String authorization) throws ParseException {
		return headerFactory.createAuthorizationHeader(authorization);
	}
	
	public Address createAddress(String address) throws ParseException {
		return addressFactory.createAddress(address);
	}
	
	public ToHeader createToHeader(String address, String displayName) throws ParseException {
		Address ad = addressFactory.createAddress(address);
		ad.setDisplayName(displayName);
		return headerFactory.createToHeader(ad, null);
	}

	public FromHeader createFromHeader(String sip, String displayName) throws ParseException {
		Address address = addressFactory.createAddress(sip);
		address.setDisplayName(displayName);
		return headerFactory.createFromHeader(address, null);
	}

	public CallIdHeader createCallIdHeader(String callId) throws ParseException {
		return headerFactory.createCallIdHeader(callId);
	}

	public CSeqHeader createCSeqHeader(long cseq, String method) throws ParseException, InvalidArgumentException {
		return headerFactory.createCSeqHeader(cseq, method);
	}
}
