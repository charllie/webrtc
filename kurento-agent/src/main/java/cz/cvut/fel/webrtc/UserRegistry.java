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

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.socket.WebSocketSession;

/**
 * Map of users registered in the system. This class has a concurrent hash map
 * to store users, using its name as key in the map.
 * 
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @author Micael Gallego (micael.gallego@gmail.com)
 * @authos Ivan Gracia (izanmail@gmail.com)
 * @since 4.3.1
 */
public class UserRegistry {

	private final ConcurrentHashMap<String, UserSession> usersByName = new ConcurrentHashMap<String, UserSession>();
	//private final ConcurrentHashMap<String, UserSession> usersBySessionId = new ConcurrentHashMap<String, UserSession>();

	public void register(UserSession user) {
		usersByName.put(user.getName(), user);
		//usersBySessionId.put(user.getSession().getId(), user);
	}

	public UserSession getByName(String name) {
		return usersByName.get(name);
	}

	/*public UserSession getBySession(WebSocketSession session) {
		return usersBySessionId.get(session.getId());
	}*/
	
	public ArrayList<UserSession> getBySession(WebSocketSession session) {
		ArrayList<UserSession> users = new ArrayList<UserSession> ();
		for (UserSession user : usersByName.values()) {
			if (user.getSession().getId().equals(session.getId()))
				users.add(user);
		}
		return users;
	}

	public boolean exists(String name) {
		return usersByName.keySet().contains(name);
	}

	public ArrayList<UserSession> removeBySession(WebSocketSession session) {
		ArrayList<UserSession> users = getBySession(session);
		
		for (final UserSession user : users) {
			
			if (user != null) {
				usersByName.remove(user.getName());
				//usersBySessionId.remove(session.getId());
			}
			
		}
		
		return users;
	}

	public void removeByName(UserSession user) {
		if (user != null)
			usersByName.remove(user.getName());
	}
	
}
