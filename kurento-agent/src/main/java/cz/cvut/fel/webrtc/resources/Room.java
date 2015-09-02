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
package cz.cvut.fel.webrtc.resources;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.PreDestroy;

import org.kurento.client.Composite;
import org.kurento.client.Continuation;
import org.kurento.client.Hub;
//import org.kurento.client.HubPort;
import org.kurento.client.MediaPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * @author Ivan Gracia (izanmail@gmail.com)
 * @since 4.3.1
 */
public class Room implements Closeable {

	private final Logger log = LoggerFactory.getLogger(Room.class);

	private final ConcurrentMap<String, Participant> participants = new ConcurrentSkipListMap<>();
	private final MediaPipeline presentationPipeline;
	private final MediaPipeline compositePipeline;
	private final Composite composite;
	private final String name;
	
	private Line line;
	private final String callId;
	private long cseq;
	private boolean closing = false;
	
	private WebUser screensharer;
	
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	public Room(String roomName, MediaPipeline compositePipeline, MediaPipeline presentationPipeline) {
		this.name = roomName;
		this.cseq = (new Random()).nextInt(100);
		this.callId = UUID.randomUUID().toString();
		this.compositePipeline = compositePipeline;
		this.presentationPipeline = presentationPipeline;
		this.composite = new Composite.Builder(compositePipeline).build();
		log.info("ROOM {} has been created", roomName);
	}

	@PreDestroy
	private void shutdown() {
		this.close();
	}

	public Participant join(String userId, WebSocketSession session, Class<? extends Participant> sessionClass) throws IOException {
		log.info("ROOM {}: adding participant {}", name, userId);
		
		Participant participant = null;
		
		try {
			
			participant = sessionClass.getConstructor(
					String.class,
					String.class,
					WebSocketSession.class,
					MediaPipeline.class,
					MediaPipeline.class,
					Hub.class)
			.newInstance(
					userId,
					this.name,
					session,
					this.compositePipeline,
					this.presentationPipeline,
					this.composite
			);
			
			if (sessionClass.isInstance(WebUser.class))
				joinRoom(participant);
			
			participants.put(participant.getId(), participant);
			sendInformation(participant, "compositeInfo");
			
		} catch (Exception e) {
			log.info("ROOM {}: adding participant {} failed: {}", name, userId, e);
		}
		
		return participant;
	}

	public void leave(Participant user) throws IOException {

		log.debug("PARTICIPANT {}: Leaving room {}", user.getName(), this.name);
		this.removeParticipant(user);
		
		if (user.equals(screensharer)) {
			this.screensharer = null;
		}
		
		user.close();
		
	}
	
	public void leave(String userId) throws IOException {
		Participant user = participants.get(userId);
		
		if (user != null)
			leave(user);
	}

	/**
	 * @param participant
	 * @throws IOException
	 */
	public void joinRoom(Participant newParticipant) throws IOException {
		final JsonObject newParticipantMsg = new JsonObject();
		newParticipantMsg.addProperty("id", "newParticipantArrived");
		newParticipantMsg.addProperty("name", newParticipant.getName());
		newParticipantMsg.addProperty("userId", newParticipant.getId());
		broadcast(newParticipantMsg, newParticipant);
	}
	
	private void broadcast(JsonObject message, Participant exception) {
		
		for (final Participant participant : participants.values()) {
			
			if (participant.equals(exception) || !(participant instanceof WebUser))
				continue;
			
			try {
				participant.sendMessage(message);
			} catch (final IOException e) {
				log.debug("ROOM {}: participant {} could not be notified",
						name, participant.getName(), e);
			}
			
		}
	}
	
	public void broadcast(JsonObject message) {
		broadcast(message, null);
	}

	private void removeParticipant(Participant participant) throws IOException {
		participants.remove(participant.getId());

		boolean isScreensharer = (screensharer != null && participant.equals(screensharer));

		log.debug("ROOM {}: notifying all users that {} is leaving the room",
				this.name, participant.getName());
	
		final JsonObject participantLeftJson = new JsonObject();
		participantLeftJson.addProperty("id", "participantLeft");
		participantLeftJson.addProperty("userId", participant.getId());
		participantLeftJson.addProperty("name", participant.getName());
		participantLeftJson.addProperty("isScreensharer", isScreensharer);
		
		final JsonArray participantsArray = new JsonArray();
		
		for (final Participant p : this.getParticipants()) {
			final JsonElement participantName = new JsonPrimitive(p.getName());
			participantsArray.add(participantName);
		}
		participantLeftJson.add("data", participantsArray);
		
		for (final Participant p : participants.values()) {
			if (p instanceof WebUser) {
				if (isScreensharer)
					((WebUser) participant).cancelPresentation();
				
				p.sendMessage(participantLeftJson);
			}
		}
		
	}

	public void cancelPresentation() throws IOException {
		if (screensharer != null) {
			final JsonObject cancelPresentationMsg = new JsonObject();
			cancelPresentationMsg.addProperty("id", "cancelPresentation");
			cancelPresentationMsg.addProperty("presenter", screensharer.getId());
			
			for (final Participant participant : participants.values()) {
				if (participant instanceof WebUser) {
					final WebUser webParticipant = (WebUser) participant;
					webParticipant.cancelPresentation();
					webParticipant.sendMessage(cancelPresentationMsg);
				}
			}
			
			screensharer = null;
		}
	}

	public void sendInformation(Participant user, String id) throws IOException {

		final JsonArray participantsArray = new JsonArray();
		
		for (final Participant participant : this.getParticipants()) {
			if (!participant.equals(user)) {
				final JsonElement participantName = new JsonPrimitive(
						participant.getName());
				participantsArray.add(participantName);
			}
		}

		final JsonObject message = new JsonObject();
		message.addProperty("id", id);
		message.add("data", participantsArray);
		message.addProperty("existingScreensharer", (screensharer != null));
		
		if (line != null)
			message.addProperty("lineExtension", line.getExtension());
		
		if (screensharer != null)
			message.addProperty("screensharer", screensharer.getName());
		
		log.debug("PARTICIPANT {}: sending a list of {} participants",
				user.getName(), participantsArray.size());
		
		user.sendMessage(message);
	}

	/**
	 * @return a collection with all the participants in the room
	 */
	public Collection<Participant> getParticipants() {
		return participants.values();
	}

	/**
	 * @param userId
	 * @return the participant from this session
	 */
	public Participant getParticipant(String id) {
		return participants.get(id);
	}

	@Override
	public void close() {
		for (final Participant user : participants.values()) {
			try {
				user.close();
			} catch (IOException e) {
				log.debug("ROOM {}: Could not invoke close on participant {}",
						this.name, user.getName(), e);
			}
		}

		participants.clear();

		compositePipeline.release(new Continuation<Void>() {

			@Override
			public void onSuccess(Void result) throws Exception {
				log.trace("ROOM {}: Released Composite Pipeline", Room.this.name);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				log.warn("PARTICIPANT {}: Could not release Composite Pipeline",
						Room.this.name);
			}
		});
		
		presentationPipeline.release(new Continuation<Void>() {

			@Override
			public void onSuccess(Void result) throws Exception {
				log.trace("ROOM {}: Released Presentation Pipeline", Room.this.name);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				log.warn("PARTICIPANT {}: Could not release Presentation Pipeline",
						Room.this.name);
			}
		});
		
		log.debug("Room {} closed", this.name);
	}
	
	public MediaPipeline getCompositePipeline() {
		return compositePipeline;
	}
	
	public MediaPipeline getPresentationPipeline() {
		return presentationPipeline;
	}
	
	public void setScreensharer(WebUser user) {
		this.screensharer = user;
	}

	public boolean hasScreensharer() {
		return (screensharer != null);
	}

	public long setCSeq(long cseq) {
		this.cseq = cseq;
		return cseq;
	}

	public long getCSeq() {
		return this.cseq;
	}

	public Line getLine() {
		return this.line;
	}
	
	public String getCallId() {
		return this.callId;
	}

	public void setLine(Line line) {
		this.line = line;
	}

	public boolean isClosing() {
		return closing;
	}

	public void setClosing() {
		this.closing = true;
	}

}
