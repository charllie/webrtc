package cz.cvut.fel.webrtc.utils;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;

public class Digest {

	public static String getHeaderResponse(String method, String uri, String response, String username, String password) throws AuthenticationException, MalformedChallengeException {

		HttpRequest request = new BasicHttpRequest(method, uri);
		DigestScheme authscheme = new DigestScheme();

		BasicHeader basicHeader = new BasicHeader(AUTH.WWW_AUTH, response);
		authscheme.processChallenge(basicHeader);

		BasicHttpContext localcontext = new BasicHttpContext();

		Header header = authscheme.authenticate(new UsernamePasswordCredentials(username, password), request, localcontext);

		return header.getValue();
	}
}
