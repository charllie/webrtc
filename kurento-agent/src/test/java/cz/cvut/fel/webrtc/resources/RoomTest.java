package cz.cvut.fel.webrtc.resources;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class RoomTest {

	private Room room;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		this.room = new Room(UUID.randomUUID().toString());
	}

	private Participant getMockParticipant() {
		Participant participant = Mockito.mock(Participant.class);
		Mockito.when(participant.getId()).thenReturn(UUID.randomUUID().toString());
		return participant;
	}

	@Test
	public void testAddParticipant() {
		int size = room.size();
		Participant participant = getMockParticipant();

		room.add(participant);
		assertEquals("Room size must have increased", room.size(), size + 1);
	}

	@Test
	public void testAddNullParticipant() {
		int size = room.size();
		room.add(null);
		assertEquals("Room size should not have changed", room.size(), size);
	}

	@Test
	public void testLeaveParticipant() throws IOException {
		// Supposing that testAddParticipant is successful
		Participant participant = getMockParticipant();
		room.add(participant);

		int size = room.size();
		room.leave(participant);

		assertEquals("Room size must have decreased", room.size(), size - 1);
	}

	@Test
	public void testLeaveUnknownParticipant() throws IOException {
		Participant participant = getMockParticipant();

		int size = room.size();
		room.leave(participant);
		assertEquals("Room size should not have changed", room.size(), size);
	}
}
