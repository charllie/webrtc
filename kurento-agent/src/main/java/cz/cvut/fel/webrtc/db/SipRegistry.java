package cz.cvut.fel.webrtc.db;

import java.util.List;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import cz.cvut.fel.webrtc.resources.Room;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix="asterisk")
public class SipRegistry {
	
	@Autowired
	private RoomManager roomManager;
	
	private List<Account> accountList;
	private boolean initialized = false;
	
	private Stack<String> freeAccounts = new Stack<String> ();
	
	private ConcurrentMap<String, Account> accounts = new ConcurrentHashMap<> ();
	private ConcurrentMap<String, String> roomByURI = new ConcurrentHashMap<>();
	
	public void init() {
		
		if (accountList != null && !accountList.isEmpty()) {
			for (Account account : accountList) {
				String username = account.getUsername();
				accounts.put(username, account);
				freeAccounts.push(username);
			}
		}
		
		initialized = true;
	}
	
	public Account getFor(Room room) {
		
		if (!initialized)
			init();

		String freeAccount = freeAccounts.pop();
		
		Account account = null;
		
		if (freeAccount != null) {
			account = accounts.get(freeAccount);
			room.setAccount(account);
		}
		
		return account;
	}

	public void releaseAccount(String account) {
		if (accounts.get(account) != null)
			freeAccounts.push(account);
	}
	
	public List<Account> getAccounts() {
		return accountList;
	}

	public void setAccounts(List<Account> accounts) {
		this.accountList = accounts;
	}
	
	public static class Account {
		private String username;
		private String password;
		
		public String getUsername() {
			return username;
		}
		
		public String getPassword() {
			return password;
		}
		
		public void setUsername(String username) {
			this.username = username;
		}
		
		public void setPassword(String password) {
			this.password = password;
		}
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
