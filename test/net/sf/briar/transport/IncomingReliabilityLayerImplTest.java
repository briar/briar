package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.FRAME_WINDOW_SIZE;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.transport.ConnectionReader;

import org.junit.Test;

public class IncomingReliabilityLayerImplTest extends BriarTestCase {

	@Test
	public void testNoReordering() throws Exception {
		List<Integer> frameNumbers = new ArrayList<Integer>();
		// Receive FRAME_WINDOW_SIZE * 2 frames in the correct order
		for(int i = 0; i < FRAME_WINDOW_SIZE * 2; i++) frameNumbers.add(i);
		IncomingAuthenticationLayer authentication =
			new TestIncomingAuthenticationLayer(frameNumbers);
		IncomingReliabilityLayerImpl reliability =
			new IncomingReliabilityLayerImpl(authentication);
		ConnectionReader reader = new ConnectionReaderImpl(reliability, false);
		InputStream in = reader.getInputStream();
		for(int i = 0; i < FRAME_WINDOW_SIZE * 2; i++) {
			for(int j = 0; j < 100; j++) assertEquals(i, in.read());
		}
		assertEquals(-1, in.read());
		// No free frames should be cached
		assertEquals(0, reliability.getFreeFramesCount());
	}

	@Test
	public void testReordering() throws Exception {
		List<Integer> frameNumbers = new ArrayList<Integer>();
		// Receive the first FRAME_WINDOW_SIZE frames in a random order
		for(int i = 0; i < FRAME_WINDOW_SIZE; i++) frameNumbers.add(i);
		Collections.shuffle(frameNumbers);
		// Receive the next FRAME_WINDOW_SIZE frames in the correct order
		for(int i = FRAME_WINDOW_SIZE; i < FRAME_WINDOW_SIZE * 2; i++) {
			frameNumbers.add(i);
		}
		// The reliability layer should reorder the frames
		IncomingAuthenticationLayer authentication =
			new TestIncomingAuthenticationLayer(frameNumbers);
		IncomingReliabilityLayerImpl reliability =
			new IncomingReliabilityLayerImpl(authentication);
		ConnectionReader reader = new ConnectionReaderImpl(reliability, false);
		InputStream in = reader.getInputStream();
		for(int i = 0; i < FRAME_WINDOW_SIZE * 2; i++) {
			for(int j = 0; j < 100; j++) assertEquals(i, in.read());
		}
		assertEquals(-1, in.read());
		// Fewer than FRAME_WINDOW_SIZE free frames should be cached
		assertTrue(reliability.getFreeFramesCount() < 32);
	}

	private static class TestIncomingAuthenticationLayer
	implements IncomingAuthenticationLayer {

		private final List<Integer> frameNumbers;

		private int index;

		private TestIncomingAuthenticationLayer(List<Integer> frameNumbers) {
			this.frameNumbers = frameNumbers;
			index = 0;
		}

		public boolean readFrame(Frame f, FrameWindow window) {
			if(index >= frameNumbers.size()) return false;
			int frameNumber = frameNumbers.get(index);
			assertTrue(window.contains(frameNumber));
			index++;
			byte[] buf = f.getBuffer();
			HeaderEncoder.encodeHeader(buf, frameNumber, 100, 0);
			for(int i = 0; i < 100; i++) {
				buf[FRAME_HEADER_LENGTH + i] = (byte) frameNumber;
			}
			f.setLength(FRAME_HEADER_LENGTH + 100 + MAC_LENGTH);
			return true;
		}

		public int getMaxFrameLength() {
			return FRAME_HEADER_LENGTH + 100 + MAC_LENGTH;
		}
	}
}
