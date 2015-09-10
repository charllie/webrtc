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

import cz.cvut.fel.webrtc.db.LineRegistry;
import cz.cvut.fel.webrtc.db.RoomManager;
import cz.cvut.fel.webrtc.db.WebRegistry;
import cz.cvut.fel.webrtc.handlers.SipHandler;
import cz.cvut.fel.webrtc.handlers.WebHandler;
import org.kurento.client.KurentoClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

/**
 * 
 * @author Ivan Gracia (izanmail@gmail.com)
 * @since 4.3.1
 */
@Configuration
@EnableWebSocket
@EnableAutoConfiguration
public class GroupCallApp implements WebSocketConfigurer {

	@Value("${kurento.websocket}")
	private String kms_uri;
	
	@Value("${xivo.websocket}")
	private String xivo_ws;
	
	@Value("${xivo.rest.uri}")
	private String xivo_rest_uri;
	
	@Value("${xivo.rest.login}")
	private String xivo_rest_login;
	
	@Value("${xivo.rest.password}")
	private String xivo_rest_password;

	@Bean
	public WebRegistry registry() {
		return new WebRegistry();
	}

	@Bean
	public RoomManager roomManager() {
		return new RoomManager();
	}

	@Bean
	public WebHandler webHandler() {
		return new WebHandler();
	}

	@Bean
	public SipHandler sipHandler() throws URISyntaxException {
		URI uri = new URI(xivo_ws);
		return new SipHandler(uri.getHost());
	}
	
	@Bean
	public LineRegistry sipRegistry() {
		return new LineRegistry(xivo_rest_uri, xivo_rest_login, xivo_rest_password);
	}
	
	@Bean
	public WebSocketConnectionManager asteriskConnection() throws URISyntaxException {
		@SuppressWarnings("Convert2Diamond") ArrayList<String> protocols = new ArrayList<>();
		protocols.add("sip");
		WebSocketConnectionManager manager = new WebSocketConnectionManager(new StandardWebSocketClient(), sipHandler(), xivo_ws);
		manager.setSubProtocols(protocols);
		manager.setAutoStartup(true);
		return manager;
	}

	@Bean
	public KurentoClient kurentoClient() {
		return KurentoClient.create(kms_uri);
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(GroupCallApp.class, args);
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(webHandler(), "/groupcall");
	}
	
	/*
	 * For ImageOverlay
	 * 
	 * @Bean
	public ImageController imageController() {
		return new ImageController();
	}*/
}
