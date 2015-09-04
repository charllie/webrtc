package cz.cvut.fel.webrtc.resources;

import static org.junit.Assert.*;

import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RoomTest {

	private Room room;
	private String roomName = UUID.randomUUID().toString();
	
	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		room = new Room(roomName);
	}
	
	@Test
	public void testJoin() {
		
		int size = room.size();
		
		Participant participant = Mockito.mock(Participant.class);
		room.add(participant);
		
		assertEquals("Unable to join room", size+1 , room.size());
	}
}