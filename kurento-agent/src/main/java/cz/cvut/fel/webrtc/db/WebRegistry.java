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

import cz.cvut.fel.webrtc.resources.WebUser;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Map of users registered in the system. This class has a concurrent hash map
 * to store users, using its name as key in the map.
 * 
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @author Micael Gallego (micael.gallego@gmail.com)
 * @author Ivan Gracia (izanmail@gmail.com)
 * @since 4.3.1
 */
public class WebRegistry {

	private final ConcurrentHashMap<String, WebUser> users = new ConcurrentHashMap<>();

	public void register(WebUser user) {
		users.put(user.getSession().getId(), user);
	}

	public Collection<WebUser> getAll() {
		return users.values();
	}
	
	public WebUser getBySession(WebSocketSession session) {
		return users.get(session.getId());
	}

	public WebUser removeBySession(WebSocketSession session) {
		final WebUser user = getBySession(session);
		
		if (user != null) {
			users.remove(session.getId());
		}
		
		return user;
	}

}
