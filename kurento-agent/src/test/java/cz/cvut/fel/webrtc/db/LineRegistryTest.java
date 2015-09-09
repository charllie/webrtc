package cz.cvut.fel.webrtc.db;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LineRegistryTest {

	private LineRegistry lineRegistry;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		this.lineRegistry = new LineRegistry();
	}
}
