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

import java.util.concurrent.ConcurrentHashMap;

import org.kurento.client.Continuation;
import org.kurento.client.EventListener;
import org.kurento.client.Hub;
import org.kurento.client.HubPort;
import org.kurento.client.IceCandidate;
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
	private final ConcurrentHashMap<String, WebRtcEndpoint> incomingMedia = new ConcurrentHashMap<String, WebRtcEndpoint>();
	private final HubPort hubPort;
	private final boolean isScreensharer;
	
	public UserSession(final String name, String roomName,
			final WebSocketSession session, MediaPipeline pipeline, Hub hub, boolean isScreensharer) {

		this.pipeline = pipeline;
		this.name = name;
		this.session = session;
		this.roomName = roomName;
		this.isScreensharer = isScreensharer;
		
		this.outgoingMedia = new WebRtcEndpoint.Builder(pipeline).build();

		this.outgoingMedia
				.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {

					@Override
					public void onEvent(OnIceCandidateEvent event) {
						JsonObject response = new JsonObject();
						response.addProperty("id", "iceCandidate");
						response.addProperty("name", name);
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
		
		this.hubPort = new HubPort.Builder(hub).build();
		
		if (!isScreensharer) {
			hubPort.connect(outgoingMedia);
			outgoingMedia.connect(hubPort);
		}
	}

	public WebRtcEndpoint getOutgoingWebRtcPeer() {
		return outgoingMedia;
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
	public void receiveVideoFrom(UserSession sender, String sdpOffer)
			throws IOException {
		log.info("USER {}: connecting with {} in room {}", this.name,
				sender.getName(), this.roomName);

		log.trace("USER {}: SdpOffer for {} is {}", this.name,
				sender.getName(), sdpOffer);
		
		final String ipSdpAnswer = this.getEndpointForUser(sender).processOffer(sdpOffer);
		final JsonObject scParams = new JsonObject();
		scParams.addProperty("id", "receiveVideoAnswer");
		scParams.addProperty("name", sender.getName());
		scParams.addProperty("sdpAnswer", ipSdpAnswer);

		log.trace("USER {}: SdpAnswer for {} is {}", this.name,
				sender.getName(), ipSdpAnswer);
		this.sendMessage(scParams);
		log.debug("gather candidates");
		this.getEndpointForUser(sender).gatherCandidates();
	}

	/**
	 * @param sender
	 *            the user
	 * @return the endpoint used to receive media from a certain user
	 */
	private WebRtcEndpoint getEndpointForUser(final UserSession sender) {
		if ((!this.isScreensharer && !sender.isScreensharer) || (this.equals(sender))) {
			log.info("PARTICIPANT {}: configuring loopback", this.name);
			return outgoingMedia;
		}
		
		WebRtcEndpoint incoming = incomingMedia.get(sender.getName());
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
	public void cancelVideoFrom(final UserSession sender) {
		this.cancelVideoFrom(sender.getName());
	}

	/**
	 * @param senderName
	 *            the participant
	 */
	public void cancelVideoFrom(final String senderName) {
		log.debug("PARTICIPANT {}: canceling video reception from {}",
				this.name, senderName);
		final WebRtcEndpoint incoming = incomingMedia.remove(senderName);		

		log.debug("PARTICIPANT {}: removing endpoint for {}", this.name,
				senderName);

		//this.hubPort.disconnect(outgoingMedia);
		//this.outgoingMedia.disconnect(hubPort);
		
		if (incoming != null) {
			incoming.release(new Continuation<Void>() {
				@Override
				public void onSuccess(Void result) throws Exception {
					log.trace(
							"PARTICIPANT {}: Released successfully incoming EP for {}",
							UserSession.this.name, senderName);
				}
	
				@Override
				public void onError(Throwable cause) throws Exception {
					log.warn(
							"PARTICIPANT {}: Could not release incoming EP for {}",
							UserSession.this.name, senderName);
				}
			});
		}
	}

	@Override
	public void close() throws IOException {
		
		log.debug("PARTICIPANT {}: Releasing resources", this.name);
		
		for (final String remoteParticipantName : incomingMedia.keySet()) {

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
		}
		
		if (hubPort != null)
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
	}

	public void sendMessage(JsonObject message) throws IOException {
		log.debug("USER {}: Sending message {}", name, message);
		synchronized (session) {
			session.sendMessage(new TextMessage(message.toString()));
		}
	}

	public void addCandidate(IceCandidate e, String name) {
		if (this.name.compareTo(name) == 0) {
			outgoingMedia.addIceCandidate(e);
		} else {
			WebRtcEndpoint webRtc = incomingMedia.get(name);
			if (webRtc != null) {
				webRtc.addIceCandidate(e);
			}
		}
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
}
