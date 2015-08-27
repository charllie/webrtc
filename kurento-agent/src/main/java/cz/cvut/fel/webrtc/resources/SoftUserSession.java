package cz.cvut.fel.webrtc.resources;

import java.io.IOException;

import org.kurento.client.Continuation;
import org.kurento.client.Hub;
import org.kurento.client.MediaPipeline;
import org.kurento.client.RtpEndpoint;
import org.kurento.client.internal.server.KurentoServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

public class SoftUserSession extends UserSession {
	
	private static final Logger log = LoggerFactory.getLogger(SoftUserSession.class);
	
	private final RtpEndpoint rtpEndpoint;
	
	public SoftUserSession(String name, String roomName, WebSocketSession session, MediaPipeline compositePipeline, MediaPipeline presentationPipeline, Hub hub) {
		super(name, roomName, session, compositePipeline, presentationPipeline, hub);
		rtpEndpoint = new RtpEndpoint.Builder(compositePipeline).build();
		
		rtpEndpoint.connect(hubPort);
		hubPort.connect(rtpEndpoint);
	}
	
	public String getSdpAnswer(String sdpOffer) {
		final String sdpAnswer = rtpEndpoint.processOffer(sdpOffer);
		log.trace("USER {}: SdpAnswer for rtp is {}", this.name, sdpAnswer);
		return sdpAnswer;
	}

	public RtpEndpoint getRtpEndpoint() {
		return rtpEndpoint;
	}
	
	public String getGeneratedOffer() {
		
		String offer = null;
		
		try {
			offer = rtpEndpoint.getLocalSessionDescriptor();
		} catch (KurentoServerException e) {}
		
		if (offer == null)
			offer = rtpEndpoint.generateOffer();
		
		return offer;
	}

	@Override
	public void close() throws IOException {
		log.debug("PARTICIPANT {}: Releasing resources", this.getName());
		release();
		
		rtpEndpoint.release(new Continuation<Void>() {

			@Override
			public void onSuccess(Void result) throws Exception {
				log.trace("PARTICIPANT {}: Released outgoing EP",
						SoftUserSession.this.getName());
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				log.warn("USER {}: Could not release outgoing EP",
						SoftUserSession.this.getName());
			}
		});
	}
}
