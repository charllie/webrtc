package cz.cvut.fel.webrtc.resources;

import com.google.gson.JsonObject;
import org.kurento.client.*;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

public class WebUser extends Participant {

	private static final Logger log = LoggerFactory.getLogger(WebUser.class);

	private WebRtcEndpoint sharingMedia;
	private boolean isScreensharer = false;

	protected final WebRtcEndpoint outgoingMedia;
	private final MediaPipeline presentationPipeline;
	
	public WebUser(final String id, String roomName, final WebSocketSession session, MediaPipeline compositePipeline, MediaPipeline presentationPipeline, Hub hub) {
		super(id, roomName, session, compositePipeline, presentationPipeline, hub);

		this.outgoingMedia = new WebRtcEndpoint.Builder(compositePipeline).build();

		this.outgoingMedia
				.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {

					@Override
					public void onEvent(OnIceCandidateEvent event) {
						
						//iceCandidates.add(event.getCandidate().getCandidate());
						
						JsonObject response = new JsonObject();
						response.addProperty("id", "iceCandidate");
						response.addProperty("userId", id);
						response.addProperty("type", "composite");
						response.add("candidate",
								JsonUtils.toJsonObject(event.getCandidate()));
						try {
							synchronized (session) {
								session.sendMessage(new TextMessage(response
										.toString()));
							}
						} catch (IOException e) {
							log.debug(e.getMessage());
						}
					}
				});

		
		
		/*
		 * For ImageOverlay
		 * 
		ImageOverlayFilter imageOverlayFilter = new ImageOverlayFilter.Builder(this.compositePipeline).build();
		imageOverlayFilter.addImage("username",  "https://webrtc.ml/names/" + UrlEscapers.urlPathSegmentEscaper().escape(name).replace(";",""), 0F, 0F, 1F, 1F, false, true);
		
		outgoingMedia.connect(imageOverlayFilter);
		imageOverlayFilter.connect(hubPort);
		hubPort.connect(outgoingMedia);
		
		 */

		outgoingMedia.connect(hubPort);
		hubPort.connect(outgoingMedia);
		
		this.presentationPipeline = presentationPipeline;
	}
	
	public String getName() {
		return this.name;
	}
	
	public WebRtcEndpoint getOutgoingWebRtcPeer() {
		return outgoingMedia;
	}

	public void setSharingMedia(WebRtcEndpoint sharingMedia) {
		this.sharingMedia = sharingMedia;
	}
	
	public WebRtcEndpoint getSharingMedia() {
		return this.sharingMedia;
	}
	
	public void receiveVideoFrom(WebUser sender, String type, String sdpOffer, Room room)
			throws IOException {
		log.info("USER {}: connecting with {} in room {}", this.name,
				sender.getName(), this.roomName);

		log.trace("USER {}: SdpOffer for {} is {}", this.name,
				sender.getName(), sdpOffer);
		
		WebRtcEndpoint ep = this.getEndpointForUser(sender, type, room);
		
		try {
			if (ep.getLocalSessionDescriptor() != null)
				return;
		} catch (Exception e) {}
		
		final String ipSdpAnswer = ep.processOffer(sdpOffer);

		log.trace("USER {}: SdpAnswer for {} is {}", this.name,
				sender.getName(), ipSdpAnswer);
		
		log.debug("gather candidates");
		ep.gatherCandidates();
		
		final JsonObject scParams = new JsonObject();
		scParams.addProperty("id", "receiveVideoAnswer");
		scParams.addProperty("userId", sender.getId());
		scParams.addProperty("name", sender.getName());
		scParams.addProperty("sdpAnswer", ipSdpAnswer);
		scParams.addProperty("type", type);
		this.sendMessage(scParams);
	}
	
	/**
	 * @param sender
	 *            the user
	 * @return the endpoint used to receive media from a certain user
	 */
	private WebRtcEndpoint getEndpointForUser(final WebUser sender, final String type, Room room) {
		
		if (!type.equals("composite")) {
			if ((this.isScreensharer && this.equals(sender)) || (sender.isScreensharer)) {
				
				if (this.sharingMedia == null) {
					
					this.sharingMedia = new WebRtcEndpoint.Builder(presentationPipeline).build();
					
					final Participant presenter = (this.isScreensharer) ? this : sender;
					
					this.sharingMedia.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
		
						@Override
						public void onEvent(OnIceCandidateEvent event) {
							JsonObject response = new JsonObject();
							response.addProperty("id", "iceCandidate");
							response.addProperty("name", presenter.getName());
							response.addProperty("userId", presenter.getId());
							response.addProperty("type", type);
							response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
							try {
								synchronized (session) {
									session.sendMessage(new TextMessage(response.toString()));
								}
							} catch (IOException e) {
								log.debug(e.getMessage());
							}
						}
					});
					
					if (this.isScreensharer && this.equals(sender)) {
						final JsonObject newPresenterMsg = new JsonObject();
						newPresenterMsg.addProperty("id", "presenterReady");
						newPresenterMsg.addProperty("userId", this.getId());
						newPresenterMsg.addProperty("presenter", this.getName());
						
						room.broadcast(newPresenterMsg);
					}
				}
				
				if (!this.isScreensharer)
					sender.getSharingMedia().connect(sharingMedia);
				
				return this.sharingMedia;
				
			}
		}
		
		return this.getOutgoingWebRtcPeer();
	}

	public void cancelPresentation() {
		log.debug("PARTICIPANT {}: canceling presentation reception", this.getName());

		log.debug("PARTICIPANT {}: removing endpoint", this.getName());
		
		if (sharingMedia != null) {
			sharingMedia.release(new Continuation<Void>() {
				@Override
				public void onSuccess(Void result) throws Exception {
					log.trace(
							"PARTICIPANT {}: Released successfully incoming EP",
							WebUser.this.getName());
				}
	
				@Override
				public void onError(Throwable cause) throws Exception {
					log.warn(
							"PARTICIPANT {}: Could not release incoming EP",
							WebUser.this.getName());
				}
			});
			
			sharingMedia = null;
		}
	}

	public void addCandidate(IceCandidate e, String type) {
		
		WebRtcEndpoint ep = (type.equals("composite")) ? this.getOutgoingWebRtcPeer() : sharingMedia;
		
		if (ep != null)
			ep.addIceCandidate(e);
	}
	
	public boolean isScreensharer() {
		return this.isScreensharer;
	}

	public void isScreensharer(boolean b) {
		this.isScreensharer = b;
	}

	@Override
	public void close() throws IOException {
		
		log.debug("PARTICIPANT {}: Releasing resources", this.getName());
		release();
		
		this.getOutgoingWebRtcPeer().release(new Continuation<Void>() {
			
			@Override
			public void onSuccess(Void result) throws Exception {
				log.trace("PARTICIPANT {}: Released outgoing EP",
						WebUser.this.getName());
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				log.warn("USER {}: Could not release outgoing EP",
						WebUser.this.getName());
			}
		});
		
		if (sharingMedia != null) {
			sharingMedia.release(new Continuation<Void>() {

				@Override
				public void onSuccess(Void result) throws Exception {
					log.trace("PARTICIPANT {}: Released outgoing EP",
							WebUser.this.getName());
				}

				@Override
				public void onError(Throwable cause) throws Exception {
					log.warn("USER {}: Could not release outgoing EP",
							WebUser.this.getName());
				}
			});
		}
	}

}
