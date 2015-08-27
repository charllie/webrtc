package cz.cvut.fel.webrtc.resources;

import java.io.IOException;

import org.kurento.client.Hub;
import org.kurento.client.MediaPipeline;
import org.kurento.client.RtpEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

public class SoftUserSession extends UserSession {
	
	private static final Logger log = LoggerFactory.getLogger(SoftUserSession.class);
	
	private final RtpEndpoint rtpEndpoint;
	
	public SoftUserSession(String name, String roomName, WebSocketSession session, MediaPipeline compositePipeline, MediaPipeline presentationPipeline, Hub hub) {
		super(name, roomName, session, compositePipeline, presentationPipeline, hub);
		rtpEndpoint = new RtpEndpoint.Builder(compositePipeline).build();
		outgoingMedia.connect(rtpEndpoint);
	}

	@Override
	public void close() throws IOException {
		log.debug("PARTICIPANT {}: Releasing resources", this.getName());
		release();
	}
	
	public String getSdpAnswer(String sdpOffer) {
		final String sdpAnswer = rtpEndpoint.processOffer(sdpOffer);
		log.trace("USER {}: SdpAnswer for rtp is {}", this.name, sdpAnswer);
		return sdpAnswer;
	}
}
