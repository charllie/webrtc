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
package cz.cvut.fel.webrtc.db;

import cz.cvut.fel.webrtc.handlers.SipHandler;
import cz.cvut.fel.webrtc.resources.Room;
import org.kurento.client.KurentoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Ivan Gracia (izanmail@gmail.com)
 * @since 4.3.1
 */
public class RoomManager {

	private final Logger log = LoggerFactory.getLogger(RoomManager.class);

	@Autowired
	private KurentoClient kurento;
	
	@Autowired
	private SipHandler sipHandler;
	
	@Autowired
	private LineRegistry sipRegistry;

	private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();

	/**
	 * @param roomName
	 *            the name of the room
	 * @return the room if it was already created, or a new one if it is the
	 *         first time this room is accessed
	 */
	public Room getRoom(String roomName) {
		log.debug("Searching for room {}", roomName);
		Room room = rooms.get(roomName);

		if (room == null) {
			log.debug("Room {} not existent. Will create now!", roomName);
			room = new Room(roomName, kurento);
			try {
				sipHandler.register(room, null);
			} catch (Exception e) {
				log.info("Room {} cannot be bound to Asterisk", roomName);
			}
			rooms.put(roomName, room);
		}
		
		log.debug("Room {} found!", roomName);
		return room;
	}
	
	public Room getRoom(String roomName, boolean create) {
		log.debug("Searching for room {}", roomName);
		Room room = rooms.get(roomName);

		if ((room == null) && create) {
			log.debug("Room {} not existent. Will create now!", roomName);
			room = new Room(roomName, kurento);
			try {
				sipHandler.register(room, null);
			} catch (Exception e) {
				log.info("Room {} cannot be bound to Asterisk", roomName);
			}
			rooms.put(roomName, room);
		}
		
		if (room != null)
			log.debug("Room {} found!", roomName);
		
		return room;
	}

	/**
	 * Removes a room from the list of available rooms
	 *
	 * @param room
	 * @throws IOException
	 */
	public void removeRoom(Room room) {
		if (room != null) {
			this.rooms.remove(room.getName());
			
			room.getCompositePipeline().release();
			room.getPresentationPipeline().release();
			
			if (room.getLine() != null) {
				sipRegistry.pushLine(room.getLine());
			}
			
			room.close();
			
			log.info("Room {} removed and closed", room.getName());
		}
	}

}
