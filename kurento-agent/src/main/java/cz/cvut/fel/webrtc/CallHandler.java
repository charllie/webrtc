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

import java.io.IOException;
import java.util.ArrayList;

import org.kurento.client.IceCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * 
 * @author Ivan Gracia (izanmail@gmail.com)
 * @since 4.3.1
 */
public class CallHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory
			.getLogger(CallHandler.class);

	private static final Gson gson = new GsonBuilder().create();

	@Autowired
	private RoomManager roomManager;

	@Autowired
	private UserRegistry registry;

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message)
			throws Exception {
		final JsonObject jsonMessage = gson.fromJson(message.getPayload(),
				JsonObject.class);

		ArrayList<UserSession> users = registry.getBySession(session);
		
		log.info("Test 1");
		
		final UserSession user = users.get(0);

		log.info("Test 2");
		
		if (user != null) {
			log.debug("Incoming message from user '{}': {}", user.getName(),
					jsonMessage);
		} else {
			log.debug("Incoming message from new user: {}", jsonMessage);
		}

		switch (jsonMessage.get("id").getAsString()) {
		case "joinRoom":
			joinRoom(jsonMessage, session);
			break;
		case "receiveVideoFrom":
			final String senderName = jsonMessage.get("sender").getAsString();
			final UserSession sender = registry.getByName(senderName);
			final String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
			users.get(0).receiveVideoFrom(sender, sdpOffer);
			break;
		case "leaveRoom":
			leaveRoom(users);
			break;
		case "onIceCandidate":
			JsonObject candidate = jsonMessage.get("candidate")
					.getAsJsonObject();

			if (user != null) {
				IceCandidate cand = new IceCandidate(candidate.get("candidate")
						.getAsString(), candidate.get("sdpMid").getAsString(),
						candidate.get("sdpMLineIndex").getAsInt());
				user.addCandidate(cand, jsonMessage.get("name").getAsString());
			}
			break;
		default:
			break;
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		//UserSession user = registry.removeBySession(session);
		ArrayList<UserSession> users = registry.removeBySession(session);
		
		for (UserSession user : users) {
			if (user != null) {
				leaveRoom(user);
			}
		}
		//roomManager.getRoom(user.getRoomName()).leave(user); déjà fait a priori dans leaveRoom
	}

	private void joinRoom(JsonObject params, WebSocketSession session)
			throws IOException {
		final String roomName = params.get("room").getAsString();
		final String name = params.get("name").getAsString();
		final String mediaSource = params.get("mediaSource").getAsString();
		final JsonObject scParams;
		
		log.info("PARTICIPANT {}: trying to join room {}", name, roomName);

		Room room = roomManager.getRoom(roomName);
		
		if (room.getParticipant(name) != null) {
			scParams = new JsonObject();
			scParams.addProperty("id", "existingName");
			synchronized (session) {
				session.sendMessage(new TextMessage(scParams.toString()));
			}
		} else if (room.hasScreensharer() && !mediaSource.equals("webcam")) {
			scParams = new JsonObject();
			scParams.addProperty("id", "existingScreensharer");
			synchronized (session) {
				session.sendMessage(new TextMessage(scParams.toString()));
			}
		} else {
			boolean isScreensharer = (!mediaSource.equals("webcam")) ? true : false;
			final UserSession user = room.join(name, session, isScreensharer);
			registry.register(user);
		}
	}

	private void leaveRoom(UserSession user) throws IOException {
		final Room room = roomManager.getRoom(user.getRoomName());
		room.leave(user);
		if (room.getParticipants().isEmpty()) {
			roomManager.removeRoom(room);
		}
		registry.removeByName(user);
	}
	
	private void leaveRoom(ArrayList<UserSession> users) throws IOException {
		for (final UserSession user : users) {
			leaveRoom(user);
		}
	}
}
