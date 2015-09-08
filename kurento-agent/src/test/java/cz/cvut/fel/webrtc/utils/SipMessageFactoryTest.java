package cz.cvut.fel.webrtc.utils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import javax.sip.InvalidArgumentException;
import javax.sip.address.Address;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.Random;
import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class SipMessageFactoryTest {

	private SipMessageFactory sipMessageFactory;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		sipMessageFactory = new SipMessageFactory();
	}

	@Test
	public void testCreateResponseFromNullRequest() {
		Request request = Mockito.mock(Request.class);
		int statusCode = 200;

		try {
			sipMessageFactory.createResponseFromRequest(request, statusCode);
		} catch (Exception e) {
			assert(e.toString().contains(NullPointerException.class.getName()));
		}
	}

	@Test
	public void testCreateResponseFromRequest() throws ParseException, InvalidArgumentException {
		String method = Request.INVITE;
		int statusCode = 200;
		String callId = UUID.randomUUID().toString();
		long cseq = (new Random()).nextInt(100);
		String fromUri = "sip:a@b.c";
		String toUri = "sip:e@f.g";
		String fromDisplayName = "D";
		String toDisplayName = "H";

		Request request = Mockito.mock(Request.class);

		FromHeader from = sipMessageFactory.createFromHeader("sip:a@b.c", "D");
		ToHeader to = sipMessageFactory.createToHeader("sip:e@f.g", "H");
		CallIdHeader callIdHeader = sipMessageFactory.createCallIdHeader(callId);
		CSeqHeader cSeqHeader = sipMessageFactory.createCSeqHeader(cseq, method);

		Mockito.when(request.getHeader("From")).thenReturn(from);
		Mockito.when(request.getHeader("To")).thenReturn(to);
		Mockito.when(request.getHeader("Call-ID")).thenReturn(callIdHeader);
		Mockito.when(request.getHeader("CSeq")).thenReturn(cSeqHeader);
		Mockito.when(request.getHeader("Via")).thenReturn(null);

		Response response = sipMessageFactory.createResponseFromRequest(request, statusCode);

		// Retrieve
		from = (FromHeader) response.getHeader("From");
		to = (ToHeader) response.getHeader("To");
		callIdHeader = (CallIdHeader) response.getHeader("Call-ID");
		cSeqHeader = (CSeqHeader) response.getHeader("CSeq");

		Address fromAddress = from.getAddress();
		Address toAddress = to.getAddress();

		// Assert
		assert(fromAddress.getURI().toString().equals(toUri));
		assert(toAddress.getURI().toString().equals(fromUri));
		assert(fromAddress.getDisplayName().equals(toDisplayName));
		assert(toAddress.getDisplayName().equals(fromDisplayName));
		assert(callIdHeader.getCallId().equals(callId));
		assert(cSeqHeader.getMethod().equals(method));
		assert(cSeqHeader.getSeqNumber() == cseq);
	}

	@Test
	public void testCloneNullResponseWithAnotherStatusCode() {
		int statusCode = 200;
		Response response = Mockito.mock(Response.class);
		try {
			sipMessageFactory.cloneResponseWithAnotherStatusCode(response, statusCode);
		} catch (Exception e) {
			assert(e.toString().contains(NullPointerException.class.getName()));
		}
	}


}
