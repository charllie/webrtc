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
import java.util.ArrayList;
import java.util.Collection;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.PreDestroy;

import org.kurento.client.Composite;
import org.kurento.client.Continuation;
//import org.kurento.client.HubPort;
import org.kurento.client.MediaPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * @author Ivan Gracia (izanmail@gmail.com)
 * @since 4.3.1
 */
public class Room implements Closeable {
	private final Logger log = LoggerFactory.getLogger(Room.class);

	private final ConcurrentMap<String, UserSession> participants = new ConcurrentHashMap<>();
	private final MediaPipeline pipeline;
	private final Composite composite;
	private final String name;
	private UserSession screensharer;
	
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	public Room(String roomName, MediaPipeline pipeline) {
		this.name = roomName;
		this.pipeline = pipeline;
		this.composite = new Composite.Builder(pipeline).build();
		log.info("ROOM {} has been created", roomName);
	}

	@PreDestroy
	private void shutdown() {
		this.close();
	}

	public UserSession join(String userName, WebSocketSession session)
			throws IOException {
		log.info("ROOM {}: adding participant {}", userName, userName);

		BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        Font font = new Font("Arial", Font.PLAIN, 48);
        g2d.setFont(font);
        FontMetrics fm = g2d.getFontMetrics();
        int width = fm.stringWidth(userName);
        int height = fm.getHeight();
        g2d.dispose();

        img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2d.setFont(font);
        fm = g2d.getFontMetrics();
        g2d.setColor(Color.MAGENTA);
        g2d.drawString(userName, 0, fm.getAscent());
        g2d.dispose();
        try {
            ImageIO.write(img, "png", new File( "src/main/resources/static/" + userName + ".png"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }

		final UserSession participant = new UserSession(userName, this.name,
				session, this.pipeline, this.composite);
		
		joinRoom(participant);
		participants.put(participant.getName(), participant);
		sendParticipantNames(participant, "compositeInfo");
		return participant;
	}

	public void leave(UserSession user) throws IOException {

		log.debug("PARTICIPANT {}: Leaving room {}", user.getName(), this.name);
		this.removeParticipant(user.getName());
		
		if (user.equals(screensharer)) {
			this.screensharer = null;
		}
		
		user.close();
		
	}

	/**
	 * @param participant
	 * @throws IOException
	 */
	private Collection<String> joinRoom(UserSession newParticipant)
			throws IOException {
		final JsonObject newParticipantMsg = new JsonObject();
		newParticipantMsg.addProperty("id", "newParticipantArrived");
		newParticipantMsg.addProperty("name", newParticipant.getName());

		return broadcast(newParticipantMsg);
	}
	
	public Collection<String> broadcast(JsonObject message) {

		final List<String> participantsList = new ArrayList<>(participants.values().size());
		
		for (final UserSession participant : participants.values()) {
			try {
				participant.sendMessage(message);
			} catch (final IOException e) {
				log.debug("ROOM {}: participant {} could not be notified",
						name, participant.getName(), e);
			}
			participantsList.add(participant.getName());
		}

		return participantsList;
	}

	private void removeParticipant(String name) throws IOException {
		participants.remove(name);

		boolean isScreensharer = (screensharer != null && name.equals(screensharer.getName()));

		log.debug("ROOM {}: notifying all users that {} is leaving the room",
				this.name, name);
	
		final JsonObject participantLeftJson = new JsonObject();
		participantLeftJson.addProperty("id", "participantLeft");
		participantLeftJson.addProperty("name", name);
		participantLeftJson.addProperty("isScreensharer", isScreensharer);
		
		for (final UserSession participant : participants.values()) {
			if (isScreensharer)
				participant.cancelPresentation();
			
			participant.sendMessage(participantLeftJson);
		}
		
	}

	public void cancelPresentation() throws IOException {
		if (screensharer != null) {
			final JsonObject cancelPresentationMsg = new JsonObject();
			cancelPresentationMsg.addProperty("id", "cancelPresentation");
			cancelPresentationMsg.addProperty("presenter", screensharer.getName());
			
			for (final UserSession participant : participants.values()) {
				participant.cancelPresentation();
				participant.sendMessage(cancelPresentationMsg);
			}
			
			screensharer = null;
		}
	}

	public void sendParticipantNames(UserSession user, String id) throws IOException {

		final JsonArray participantsArray = new JsonArray();
		
		for (final UserSession participant : this.getParticipants()) {
			if (!participant.equals(user)) {
				final JsonElement participantName = new JsonPrimitive(
						participant.getName());
				participantsArray.add(participantName);
			}
		}

		final JsonObject existingParticipantsMsg = new JsonObject();
		existingParticipantsMsg.addProperty("id", id);
		existingParticipantsMsg.add("data", participantsArray);
		existingParticipantsMsg.addProperty("existingScreensharer", (screensharer != null));
		
		if (screensharer != null)
			existingParticipantsMsg.addProperty("screensharer", screensharer.getName());
		
		log.debug("PARTICIPANT {}: sending a list of {} participants",
				user.getName(), participantsArray.size());
		
		user.sendMessage(existingParticipantsMsg);
	}

	/**
	 * @return a collection with all the participants in the room
	 */
	public Collection<UserSession> getParticipants() {
		return participants.values();
	}

	/**
	 * @param name
	 * @return the participant from this session
	 */
	public UserSession getParticipant(String name) {
		return participants.get(name);
	}

	@Override
	public void close() {
		for (final UserSession user : participants.values()) {
			try {
				user.close();
			} catch (IOException e) {
				log.debug("ROOM {}: Could not invoke close on participant {}",
						this.name, user.getName(), e);
			}
		}

		participants.clear();

		pipeline.release(new Continuation<Void>() {

			@Override
			public void onSuccess(Void result) throws Exception {
				log.trace("ROOM {}: Released Pipeline", Room.this.name);
			}

			@Override
			public void onError(Throwable cause) throws Exception {
				log.warn("PARTICIPANT {}: Could not release Pipeline",
						Room.this.name);
			}
		});

		log.debug("Room {} closed", this.name);
	}
	
	public MediaPipeline getPipeline() {
		return pipeline;
	}
	
	public void setScreensharer(UserSession user) {
		this.screensharer = user;
	}

	public boolean hasScreensharer() {
		return (screensharer != null);
	}

}
