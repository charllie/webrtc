package cz.cvut.fel.webrtc.db;

import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.commons.io.IOUtils;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.BasicHttpContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import cz.cvut.fel.webrtc.resources.Line;
import cz.cvut.fel.webrtc.resources.Room;

@Configuration
@EnableConfigurationProperties
public class LineRegistry {
	
	@Autowired
	private RoomManager roomManager;
	
	private Stack<Line> lines = new Stack<Line> ();
	private ConcurrentMap<String, String> roomByURI = new ConcurrentHashMap<>();
	
	private String roomPattern = "Room ([0-9]+)";
	
	public LineRegistry(String strUri, String login, String password) {
		
		if (strUri == null || login == null || password == null)
			return;

	    try {

	    	URI uri = new URI(strUri);
			HttpGet get = new HttpGet(uri);

			CloseableHttpClient client = getSSLClient();
			CloseableHttpResponse response = client.execute(get);

			int statusCode = response.getStatusLine().getStatusCode();

			if (statusCode == 401) {
				response = processDigest(client, get, response, login, password);
				statusCode = response.getStatusLine().getStatusCode();
			}

			if (statusCode == 200)
				processContent(response);

			client.close();
			
			// Only keep lines for rooms
			filterLines(Pattern.compile(roomPattern));
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void filterLines(Pattern p) {
		Matcher m;
		for (Iterator<Line> iterator = lines.iterator(); iterator.hasNext();) {
		    Line line = iterator.next();
		    m = p.matcher(line.getName());
		    
		    if (!m.matches())
		    	iterator.remove();
		}
	}

	private void processContent(CloseableHttpResponse response) throws IllegalStateException, IOException {

		String items = IOUtils.toString(response.getEntity().getContent());
		Gson gson = new GsonBuilder().create();
		String json = gson.fromJson(items, JsonObject.class).get("items").toString();
		
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		JavaType list = mapper.getTypeFactory().constructCollectionType(Stack.class, Line.class);
		ObjectReader objectReader = mapper.reader(list);
		lines = objectReader.readValue(json);
		
	}

	private CloseableHttpClient getSSLClient() throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {

		SSLContext sslContext = new SSLContextBuilder()
				.loadTrustMaterial(null, new TrustSelfSignedStrategy())
				.build();
		
		SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
		
		 return HttpClients
			.custom()
	        .setSSLSocketFactory(sslConnectionSocketFactory)
	        .build();
	}

	private CloseableHttpResponse processDigest(CloseableHttpClient client, HttpGet get, CloseableHttpResponse response, String login, String password) throws MalformedChallengeException, AuthenticationException, ClientProtocolException, IOException {

		URI uri = get.getURI();
    	HttpHost host = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
		DigestScheme authscheme = new DigestScheme();
		authscheme.processChallenge(response.getFirstHeader(AUTH.WWW_AUTH));
		
		BasicHttpContext localcontext = new BasicHttpContext();
		AuthCache authCache = new BasicAuthCache();
		authCache.put(host, authscheme);
	    localcontext.setAttribute(HttpClientContext.AUTH_CACHE, authCache);
		
		Header headerResponse = authscheme.authenticate(new UsernamePasswordCredentials(login, password), get, localcontext);

		get.addHeader(headerResponse);
		get.addHeader("Cookie", response.getFirstHeader("Set-Cookie").getValue());
		get.addHeader("Accept", MediaType.APPLICATION_JSON_VALUE);
		
		return client.execute(get);
	}

	public Line popLine(Room room) {
	
		Line line = lines.pop();
		
		if (line != null)
			room.setLine(line);
		
		return line;
	}
	
	public void pushLine(Line line) {
		lines.push(line);
	}
	
	public void addRoomByURI(String uri, String room) {
		roomByURI.put(uri, room);
	}

	public Room getRoomBySipURI(String uri) {
		String roomName = roomByURI.get(uri);
		Room room = null;
		
		if (roomName != null)
			room = roomManager.getRoom(roomName);
		
		return room;
	}
}