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
package cz.cvut.fel.webrtc.handlers;

import java.io.IOException;

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

import cz.cvut.fel.webrtc.db.RoomManager;
import cz.cvut.fel.webrtc.db.WebRegistry;
import cz.cvut.fel.webrtc.resources.Room;
import cz.cvut.fel.webrtc.resources.Participant;
import cz.cvut.fel.webrtc.resources.WebUser;

/**
 * 
 * @author Ivan Gracia (izanmail@gmail.com)
 * @since 4.3.1
 */
public class WebHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(WebHandler.class);

	private static final Gson gson = new GsonBuilder().create();

	@Autowired
	private RoomManager roomManager;

	@Autowired
	private WebRegistry registry;
	
	@Autowired
	private SipHandler sipHandler;

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		
		final JsonObject jsonMessage = gson.fromJson(message.getPayload(),
				JsonObject.class);

		final Participant userSession = registry.getBySession(session);
		WebUser user = null;
		
		if (userSession != null) {
			log.debug("Incoming message from user '{}': {}", userSession.getName(), jsonMessage);
			user = (WebUser) userSession;
		} else {
			log.debug("Incoming message from new user: {}", jsonMessage);
		}

		switch (jsonMessage.get("id").getAsString()) {
		case "invite":
			if (user != null) {
				String callee = jsonMessage.get("callee").getAsString();
				Room room = roomManager.getRoom(user.getRoomName());
				
				if (room.getAccount() != null)
					invite(room, callee);
			}
			break;

		case "joinRoom":
			joinRoom(jsonMessage, session);
			break;
		
		case "newPresenter":
			if (user != null)
				presenter(user);
			break;
			
		case "presenterReady":
			if (user != null) {
				Room room = roomManager.getRoom(user.getRoomName());
				
				final JsonObject newPresenterMsg = new JsonObject();
				newPresenterMsg.addProperty("id", "presenter");
				newPresenterMsg.addProperty("name", user.getName());
				
				room.broadcast(newPresenterMsg);
			}
			break;
			
		case "stopPresenting":
			if (user != null)
				stopPresenting(user);
			break;
			
		case "receiveVideoFrom":
			if (user != null) {
				final String senderName = jsonMessage.get("sender").getAsString();
				final Room room = roomManager.getRoom(user.getRoomName());
				final Participant sender = room.getParticipant(senderName);
				
				if (sender != null && (sender instanceof WebUser)) {
					final WebUser webSender = (WebUser) sender;
					final String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
					final String type = jsonMessage.get("type").getAsString();
					user.receiveVideoFrom(webSender, type, sdpOffer, room);
				}
			}
			
			break;
		
		case "leaveRoom":
			if (user != null)
				leaveRoom(user);
			break;
		
		case "onIceCandidate":
			JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();

			if (user != null) {
				IceCandidate cand = new IceCandidate(candidate.get("candidate")
						.getAsString(), candidate.get("sdpMid").getAsString(),
						candidate.get("sdpMLineIndex").getAsInt());
				user.addCandidate(cand, jsonMessage.get("type").getAsString());
			}
			break;
		default:
			break;
		}
	}

	private void invite(Room room, String callee) {
		sipHandler.generateInviteRequest(room, callee);
	}

	private void stopPresenting(WebUser user) throws IOException {
		if (user.isScreensharer()) {
			final Room room = roomManager.getRoom(user.getRoomName());
			user.isScreensharer(false);
			room.cancelPresentation();
		}
	}

	private void presenter(WebUser user) throws IOException {
		Room room = roomManager.getRoom(user.getRoomName());
		
		if (!room.hasScreensharer()) {
			
			room.sendParticipantNames(user, "presentationInfo");
			room.setScreensharer(user);
			user.isScreensharer(true);
			
		} else {
			
			JsonObject msg = new JsonObject();
			WebSocketSession session = user.getSession();
			
			msg.addProperty("id", "existingPresentation");
			synchronized (session) {
				session.sendMessage(new TextMessage(msg.toString()));
			}
			
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		//UserSession user = registry.removeBySession(session);
		Participant user = registry.removeBySession(session);
		
		if (user != null) {
			leaveRoom(user);
		}
	}

	private void joinRoom(JsonObject params, WebSocketSession session) throws IOException {
		final String roomName = params.get("room").getAsString();
		final String name = params.get("name").getAsString();
		final JsonObject scParams;
		
		log.info("PARTICIPANT {}: trying to join room {}", name, roomName);

		Room room = roomManager.getRoom(roomName);
		
		if (room.getParticipant(name) != null) {
			scParams = new JsonObject();
			scParams.addProperty("id", "existingName");
			synchronized (session) {
				session.sendMessage(new TextMessage(scParams.toString()));
			}
		} else {
			final WebUser user = (WebUser) room.join(name, session, WebUser.class);
			
			if (user != null)
				registry.register(user);
		}
	}

	private void leaveRoom(Participant user) throws IOException {
		if (user != null) {
			final Room room = roomManager.getRoom(user.getRoomName());
			room.leave(user);
			if (room.getParticipants().isEmpty()) {
				roomManager.removeRoom(room);
			}
			registry.removeBySession(user.getSession());
		}
	}
}
