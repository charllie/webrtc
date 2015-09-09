package cz.cvut.fel.webrtc.resources;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.UUID;

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
		assert(room.size() == size + 1);
	}

	@Test
	public void testAddNullParticipant() {
		int size = room.size();
		Participant participant = null;

		room.add(null);
		assert(room.size() == size);
	}

	@Test
	public void testLeaveParticipant() throws IOException {
		// Supposing that testAddParticipant is successful
		Participant participant = getMockParticipant();
		room.add(participant);

		int size = room.size();
		room.leave(participant);

		assert(room.size() == size - 1);
	}

	@Test
	public void testLeaveUnknownParticipant() throws IOException {
		Participant participant = getMockParticipant();

		int size = room.size();
		room.leave(participant);
		assert(room.size() == size);
	}
}
