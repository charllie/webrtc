package cz.cvut.fel.webrtc.db;

import cz.cvut.fel.webrtc.resources.Room;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.UUID;

@RunWith(MockitoJUnitRunner.class)
public class LineRegistryTest {

	private LineRegistry lineRegistry;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		this.lineRegistry = new LineRegistry();
	}

	@Test
	public void
}
