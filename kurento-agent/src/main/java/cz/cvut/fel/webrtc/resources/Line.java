package cz.cvut.fel.webrtc.resources;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Line {
	private String name;
	private String username;
	private String secret;
	private String callerid;
	private String extension;
	
	public String getUsername() {
		return username;
	}
	
	public String getSecret() {
		return secret;
	}
	
	public void setUsername(String username) {
		this.username = username;
	}
	
	public void setPassword(String secret) {
		this.secret = secret;
	}
	
	public String getCallerid() {
		return this.callerid;
	}
	
	public void setCallerid(String callerid) {
		this.callerid = callerid;
	}
	
	public String getName() {
		if (name == null)
			setProperties();
		return name;
	}
	
	public String getExtension() {
		if (extension == null)
			setProperties();
		return extension;
	}
	
	private void setProperties() {
		if (extension == null || name == null) {
			Pattern p = Pattern.compile("\"(.*)\" <([0-9]+)>");     
			Matcher m = p.matcher(callerid);
			
			try {
				if (m.matches()) {
					name = m.group(1);
					extension = m.group(m.groupCount());
				}
			} catch (Exception e) {}
		}
	}
}