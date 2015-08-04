/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */
package cz.cvut.fel.webrtc;

import java.io.Closeable;
import java.io.IOException;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.ConcurrentMap;








import org.kurento.client.ConnectionStateChangedEvent;
import org.kurento.client.Continuation;
import org.kurento.client.ElementDisconnectedEvent;
import org.kurento.client.ErrorEvent;
import org.kurento.client.EventListener;
import org.kurento.client.ImageOverlayFilter; 
import org.kurento.client.Hub;
import org.kurento.client.HubPort;
import org.kurento.client.IceCandidate;
import org.kurento.client.ListenerSubscription;
import org.kurento.client.MediaPipeline;
import org.kurento.client.OnIceCandidateEvent;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonObject;

/**
 * 
 * @author Ivan Gracia (izanmail@gmail.com)
 * @since 4.3.1
 */
public class UserSession implements Closeable {

	private static final Logger log = LoggerFactory
			.getLogger(UserSession.class);

	private final String name;
	private final WebSocketSession session;
	
	private final MediaPipeline pipeline;

	private final String roomName;
	
	private final WebRtcEndpoint outgoingMedia;
	private WebRtcEndpoint sharingMedia; 
	
	//private final ConcurrentHashMap<String, WebRtcEndpoint> incomingMedia = new ConcurrentHashMap<String, WebRtcEndpoint>();
	private final HubPort hubPort;
	private boolean isScreensharer = false;
	
	public UserSession(final String name, String roomName,
			final WebSocketSession session, MediaPipeline pipeline, Hub hub) {

		this.pipeline = pipeline;
		this.name = name;
		this.session = session;
		this.roomName = roomName;
		
		this.outgoingMedia = new WebRtcEndpoint.Builder(pipeline).build();

		this.outgoingMedia
				.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {

					@Override
					public void onEvent(OnIceCandidateEvent event) {
						JsonObject response = new JsonObject();
						response.addProperty("id", "iceCandidate");
						response.addProperty("name", name);
						response.addProperty("type", "webcam");
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
		
		this.outgoingMedia.addConnectionStateChangedListener(new EventListener<ConnectionStateChangedEvent>() {
			
			@Override
			public void onEvent(ConnectionStateChangedEvent event) {
				System.out.println("--------------------------");
				System.out.println("connection state changed event (sync)");
				System.out.println("--------------------------");
			}
			
		});
		
		this.outgoingMedia.addConnectionStateChangedListener(new EventListener<ConnectionStateChangedEvent>() {
			
			@Override
			public void onEvent(ConnectionStateChangedEvent event) {
				System.out.println("--------------------------");
				System.out.println("connection state changed event (async)");
				System.out.println("--------------------------");
			}
			
		}, new Continuation<ListenerSubscription>() {

			@Override
			public void onError(Throwable cause) throws Exception {
				System.out.println("--------------------------");
				System.out.println("connection state changed event (async - on error)");
				System.out.println("--------------------------");
			}

			@Override
			public void onSuccess(ListenerSubscription result) throws Exception {
				System.out.println("--------------------------");
				System.out.println("connection state changed event (async - on success)");
				System.out.println("--------------------------");
			}
		});
		
		this.outgoingMedia.addElementDisconnectedListener(new EventListener<ElementDisconnectedEvent> () {
			
			@Override
			public void onEvent(ElementDisconnectedEvent event) {
				System.out.println("--------------------------");
				System.out.println("element disconnected event (sync)");
				System.out.println("--------------------------");
			}
			
		});
		
		this.outgoingMedia.addElementDisconnectedListener(new EventListener<ElementDisconnectedEvent> () {
			
			@Override
			public void onEvent(ElementDisconnectedEvent event) {
				System.out.println("--------------------------");
				System.out.println("element disconnected event (async)");
				System.out.println("--------------------------");
			}
			
		}, new Continuation<ListenerSubscription>() {

			@Override
			public void onError(Throwable cause) throws Exception {
				System.out.println("--------------------------");
				System.out.println("element disconnected event (async - on error)");
				System.out.println("--------------------------");
			}

			@Override
			public void onSuccess(ListenerSubscription result) throws Exception {
				System.out.println("--------------------------");
				System.out.println("element disconnected event (async - on success)");
				System.out.println("--------------------------");
			}
		});
		
		this.outgoingMedia.addErrorListener(new EventListener<ErrorEvent> () {
			@Override
			public void onEvent(ErrorEvent event) {
				System.out.println("--------------------------");
				System.out.println("error event (sync)");
				System.out.println("--------------------------");
			}
		});
		
		this.outgoingMedia.addErrorListener(new EventListener<ErrorEvent> () {
			@Override
			public void onEvent(ErrorEvent event) {
				System.out.println("--------------------------");
				System.out.println("error event (async)");
				System.out.println("--------------------------");
			}
			
		}, new Continuation<ListenerSubscription>() {

			@Override
			public void onError(Throwable cause) throws Exception {
				System.out.println("--------------------------");
				System.out.println("error event (async - on error)");
				System.out.println("--------------------------");
			}

			@Override
			public void onSuccess(ListenerSubscription result) throws Exception {
				System.out.println("--------------------------");
				System.out.println("error event (async - on success)");
				System.out.println("--------------------------");
			}
		});

		ImageOverlayFilter imageOverlayFilter = new ImageOverlayFilter.Builder(this.pipeline).build();
		
        imageOverlayFilter.addImage("icon", "file:///webrtc/kurento-agent/icon_small.png", 0, 0, 1, 1, true, true);


		
		this.hubPort = new HubPort.Builder(hub).build();
		outgoingMedia.connect(imageOverlayFilter);
		imageOverlayFilter.connect(hubPort);
		hubPort.connect(outgoingMedia);
		
		
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
	
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the session
	 */
	public WebSocketSession getSession() {
		return session;
	}

	/**
	 * The room to which the user is currently attending
	 * 
	 * @return The room
	 */
	public String getRoomName() {
		return this.roomName;
	}

	/**
	 * @param sender
	 * @param sdpOffer
	 * @throws IOException
	 */
	public void receiveVideoFrom(UserSession sender, String type, String sdpOffer, Room room)
			throws IOException {
		log.info("USER {}: connecting with {} in room {}", this.name,
				sender.getName(), this.roomName);

		log.trace("USER {}: SdpOffer for {} is {}", this.name,
				sender.getName(), sdpOffer);
		
		WebRtcEndpoint ep = this.getEndpointForUser(sender, type, room);
		
		final String ipSdpAnswer = ep.processOffer(sdpOffer);
		final JsonObject scParams = new JsonObject();
		scParams.addProperty("id", "receiveVideoAnswer");
		scParams.addProperty("name", sender.getName());
		scParams.addProperty("sdpAnswer", ipSdpAnswer);
		scParams.addProperty("type", type);

		log.trace("USER {}: SdpAnswer for {} is {}", this.name,
				sender.getName(), ipSdpAnswer);
		this.sendMessage(scParams);
		log.debug("gather candidates");
		ep.gatherCandidates();
	}

	/**
	 * @param sender
	 *            the user
	 * @return the endpoint used to receive media from a certain user
	 */
	private WebRtcEndpoint getEndpointForUser(final UserSession sender, final String type, Room room) {
		
		if (!type.equals("webcam")) {
			if ((this.isScreensharer && this.equals(sender)) || (sender.isScreensharer)) {
				
				if (this.sharingMedia == null) {
					this.sharingMedia = new WebRtcEndpoint.Builder(pipeline).build();
					
					final UserSession presenter = (this.isScreensharer) ? this : sender;
					
					this.sharingMedia.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
		
						@Override
						public void onEvent(OnIceCandidateEvent event) {
							JsonObject response = new JsonObject();
							response.addProperty("id", "iceCandidate");
							response.addProperty("name", presenter.getName());
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
						newPresenterMsg.addProperty("presenter", this.getName());
						
						room.broadcast(newPresenterMsg);
					}
				}
				
				if (!this.isScreensharer)
					sender.getSharingMedia().connect(sharingMedia);
				
				return this.sharingMedia;
				
			}
		}
		
		return outgoingMedia;
		
		
		
		/*WebRtcEndpoint incoming = incomingMedia.get(sender.getName());
		if (incoming == null) {
			incoming = new WebRtcEndpoint.Builder(pipeline).build();
			
			log.info("PARTICIPANT {}: creating new endpoint for {}", this.name, sender.getName());
	
			incoming.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
	
				@Override
				public void onEvent(OnIceCandidateEvent event) {
					JsonObject response = new JsonObject();
					response.addProperty("id", "iceCandidate");
					response.addProperty("name", sender.getName());
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
			
			incomingMedia.put(sender.getName(), incoming);
		}

		log.info("PARTICIPANT {}: obtained endpoint for {}", this.name, sender.getName());
		
		sender.getOutgoingWebRtcPeer().connect(incoming);
		
		if (isScreensharer && !this.equals(sender)) {
			hubPort.connect(incoming);
			incoming.connect(hubPort);
		}
		
		return incoming;

		/*log.info("PARTICIPANT {}: receiving video from {}", this.name,
				sender.getName());

		WebRtcEndpoint incoming = incomingMedia.get(sender.getName());
		if (incoming == null) {
			log.info("PARTICIPANT {}: creating new endpoint for {}",
					this.name, sender.getName());
			incoming = new WebRtcEndpoint.Builder(pipeline).build();

			incoming.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {

				@Override
				public void onEvent(OnIceCandidateEvent event) {
					JsonObject response = new JsonObject();
					response.addProperty("id", "iceCandidate");
					response.addProperty("name", sender.getName());
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

			incomingMedia.put(sender.getName(), incoming);
		}

		log.info("PARTICIPANT {}: obtained endpoint for {}", this.name,
				sender.getName());
		sender.getOutgoingWebRtcPeer().connect(incoming);

		return incoming;*/
	}

	/**
	 * @param sender
	 *            the participant
	 */
	/*public void cancelVideoFrom(final UserSession sender) {
		this.cancelVideoFrom(sender.getName());
	}*/

	/**
	 * @param senderName
	 *            the participant
	 */
	public void cancelPresentation() {
		log.debug("PARTICIPANT {}: canceling presentation reception", this.name);

		log.debug("PARTICIPANT {}: removing endpoint", this.name);

		//this.hubPort.disconnect(outgoingMedia);
		//this.outgoingMedia.disconnect(hubPort);
		
		if (sharingMedia != null) {
			sharingMedia.release(new Continuation<Void>() {
				@Override
				public void onSuccess(Void result) throws Exception {
					log.trace(
							"PARTICIPANT {}: Released successfully incoming EP",
							UserSession.this.name);
				}
	
				@Override
				public void onError(Throwable cause) throws Exception {
					log.warn(
							"PARTICIPANT {}: Could not release incoming EP",
							UserSession.this.name);
				}
			});
			
			sharingMedia = null;
		}
	}

	@Override
	public void close() throws IOException {
		
		log.debug("PARTICIPANT {}: Releasing resources", this.name);
		
		/*for (final String remoteParticipantName : incomingMedia.keySet()) {

			log.info("PARTICIPANT {}: Released incoming EP for {}", this.name,
					remoteParticipantName);

			final WebRtcEndpoint ep = this.incomingMedia
					.get(remoteParticipantName);
			
			if (ep != null) {
				ep.release(new Continuation<Void>() {
	
					@Override
					public void onSuccess(Void result) throws Exception {
						log.trace(
								"PARTICIPANT {}: Released successfully incoming EP for {}",
								UserSession.this.name, remoteParticipantName);
					}
	
					@Override
					public void onError(Throwable cause) throws Exception {
						log.warn("PARTICIPANT {}: Could not release incoming EP for {}", UserSession.this.name, remoteParticipantName);
					}
				});
			}
		}*/
		
		hubPort.release();
		
		outgoingMedia.release(new Continuation<Void>() {

			@Override
			public void onSuccess(Void result) throws Exception {
				log.trace("PARTICIPANT {}: Released outgoing EP",
						UserSession.this.name);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				log.warn("USER {}: Could not release outgoing EP",
						UserSession.this.name);
			}
		});
		
		if (sharingMedia != null) {
			sharingMedia.release(new Continuation<Void>() {

				@Override
				public void onSuccess(Void result) throws Exception {
					log.trace("PARTICIPANT {}: Released outgoing EP",
							UserSession.this.name);
				}

				@Override
				public void onError(Throwable cause) throws Exception {
					log.warn("USER {}: Could not release outgoing EP",
							UserSession.this.name);
				}
			});
		}
	}

	public void sendMessage(JsonObject message) throws IOException {
		log.debug("USER {}: Sending message {}", name, message);
		synchronized (session) {
			session.sendMessage(new TextMessage(message.toString()));
		}
	}

	public void addCandidate(IceCandidate e, String type) {
		WebRtcEndpoint ep = (type.equals("composite")) ? outgoingMedia : sharingMedia;
		
		if (ep != null)
			ep.addIceCandidate(e);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}
		if (obj == null || !(obj instanceof UserSession)) {
			return false;
		}
		UserSession other = (UserSession) obj;
		boolean eq = name.equals(other.name);
		eq &= roomName.equals(other.roomName);
		return eq;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		int result = 1;
		result = 31 * result + name.hashCode();
		result = 31 * result + roomName.hashCode();
		return result;
	}
	
	public boolean isScreensharer() {
		return this.isScreensharer;
	}

	public void isScreensharer(boolean b) {
		this.isScreensharer = b;
	}
}
