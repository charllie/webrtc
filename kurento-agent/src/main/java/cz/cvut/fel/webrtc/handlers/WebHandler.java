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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import cz.cvut.fel.webrtc.db.LineRegistry;
import cz.cvut.fel.webrtc.db.RoomManager;
import cz.cvut.fel.webrtc.db.WebRegistry;
import cz.cvut.fel.webrtc.resources.Participant;
import cz.cvut.fel.webrtc.resources.Room;
import cz.cvut.fel.webrtc.resources.WebUser;
import org.kurento.client.IceCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

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
	private LineRegistry lineRegistry;
	
	@Autowired
	private SipHandler sipHandler;

	public WebHandler() {
		super();
		TimerTask task = new TimerTask() {

			@Override
			public void run() {
				Calendar currentDate = Calendar.getInstance();

				for (WebUser user : registry.getAll()) {
					Calendar ping = user.getLastPing();
					ping.add(Calendar.SECOND, 40);

					if (ping.before(currentDate)) {
						try {
							log.info("{} is unreachable.", user.getName());
							leaveRoom(user);
						} catch (Exception e) {}
					}
				}
			}

		};

		Timer timer = new Timer();
		timer.scheduleAtFixedRate(task, 30000, 30000);
	}

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
				String extension = jsonMessage.get("callee").getAsString();
				Room room = roomManager.getRoom(user.getRoomName());
				
				if (room.getLine() != null) {
					invite(room, extension, user.getName());
				}

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
				newPresenterMsg.addProperty("userId", user.getId());
				
				room.broadcast(newPresenterMsg);
			}
			break;
			
		case "stopPresenting":
			if (user != null)
				stopPresenting(user);
			break;
			
		case "receiveVideoFrom":
			if (user != null) {
				final String senderId = jsonMessage.get("userId").getAsString();
				final Room room = roomManager.getRoom(user.getRoomName());
				final Participant sender = room.getParticipant(senderId);

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

		case "renew":
			// maybe need a secure to avoid to abuse this
			if (user != null) {
				user.renewOutgoingMedia();
			}
			break;

		case "stay-alive":
			if (user != null) {
				user.setLastPing(Calendar.getInstance());
			}
			break;

		default:
			break;
		}
	}

	private void invite(Room room, String extension, String caller) {
		final JsonObject callInfo = new JsonObject();
		callInfo.addProperty("id", "callInformation");
		callInfo.addProperty("message", String.format("%s is calling no. %s", caller, extension));
		room.broadcast(callInfo);

		final JsonObject callError = new JsonObject();
		callError.addProperty("id", "callInformation");

		if (!lineRegistry.isCallable(extension)) {
			callError.addProperty("message", String.format("%s is unreachable.", extension));
			room.broadcast(callError);
			return;
		}

		String sipAddress = String.format("sip:%s@%s", extension, sipHandler.getPbxIp());

		Participant participant = room.getParticipant(sipAddress);

		if (participant != null) {
			String name = participant.getName();

			if (name == null)
				name = extension;

			callError.addProperty("message", String.format("%s is already in the room.", name));
			return;
		}

		sipHandler.generateInviteRequest(room, extension);

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
			
			room.sendInformation(user, "presentationInfo");
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

		Participant user = registry.removeBySession(session);
		
		if (user != null) {
			leaveRoom(user);
		}
	}

	private void joinRoom(JsonObject params, WebSocketSession session) throws IOException {
		final String roomName = params.get("room").getAsString();
		final String userId = params.get("userId").getAsString();
		final String name = params.get("name").getAsString();
		final JsonObject scParams;
		
		log.info("PARTICIPANT {}: trying to join room {}", name, roomName);

		Room room = roomManager.getRoom(roomName);
		// TODO
		/*if (room.getParticipant(name) != null) {
			scParams = new JsonObject();
			scParams.addProperty("id", "existingName");
			synchronized (session) {
				session.sendMessage(new TextMessage(scParams.toString()));
			}
		} else {*/
			final WebUser user = (WebUser) room.join(userId, session, WebUser.class);
			user.setName(name);
			room.joinRoom(user);

			if (user != null)
				registry.register(user);
		//}
	}

	private void leaveRoom(Participant user) throws Exception {
		if (user != null) {
			final Room room = roomManager.getRoom(user.getRoomName());
			room.leave(user);
			if (room.getParticipants().isEmpty()) {
				if (room.getLine() == null)
					roomManager.removeRoom(room);
				else
					sipHandler.unregister(room);
			}
			registry.removeBySession(user.getSession());
		}
	}
}
