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

import org.kurento.client.Hub;
import org.kurento.client.HubPort;
import org.kurento.client.MediaPipeline;
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
public abstract class UserSession implements Closeable {

	private static final Logger log = LoggerFactory.getLogger(UserSession.class);

	protected final String name;
	protected final WebSocketSession session;

	protected final String roomName;
	
	//private final HashSet<String> iceCandidates = new HashSet<String> ();
	
	protected final HubPort hubPort;
	
	public UserSession(final String name, String roomName,
			final WebSocketSession session, MediaPipeline compositePipeline, MediaPipeline presentationPipeline, Hub hub){

		this.name = name;
		this.session = session;
		this.roomName = roomName;
		
		this.hubPort = new HubPort.Builder(hub).build();		
		
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

	public void sendMessage(JsonObject message) throws IOException {
		log.debug("USER {}: Sending message {}", name, message);
		synchronized (session) {
			session.sendMessage(new TextMessage(message.toString()));
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

	/*public HashSet<String> getCandidates() {
		return iceCandidates;
	}

	private void addCandidates(String sdpOffer) {
		/*Pattern p = Pattern.compile("a=candidate:(.*)");
		Matcher m;
		IceCandidate e;
		
		String[] lines = sdpOffer.replace("\r\n", "\n").split("\n");
		
		if (lines != null) {
			for (String line : lines) {
	            m = p.matcher(line);
	            
	            if (m.find()) {
	            	try {
	            		System.out.println("candidate:" + m.group(1));
		            	e = new IceCandidate("candidate:" + m.group(1), "audio", 0);
		            	addCandidate(e, "composite");
	            	} catch (Exception ex) {}
	            }
	        }
		}
		
		System.out.println("done");
		
	}*/

	@Override
	public abstract void close() throws IOException;
	
	protected void release() {
		hubPort.release();
	}
}
