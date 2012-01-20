package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;

import java.util.Random;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.transport.Segment;

import static org.junit.Assert.assertArrayEquals;
import org.junit.Test;

public class XorErasureCodeTest extends BriarTestCase {

	@Test
	public void testEncodingAndDecodingWithAllSegments() throws Exception {
		XorErasureEncoder e = new XorErasureEncoder(5);
		XorErasureDecoder d = new XorErasureDecoder(5);
		Frame f = new Frame(1234);
		new Random().nextBytes(f.getBuffer());
		int payload = 1234 - FRAME_HEADER_LENGTH - MAC_LENGTH;
		HeaderEncoder.encodeHeader(f.getBuffer(), 0L, payload, 0);
		f.setLength(1234);
		Segment[] set = e.encodeFrame(f);
		assertEquals(5, set.length);
		Frame f1 = new Frame(1234);
		assertTrue(d.decodeFrame(f1, set));
		assertArrayEquals(f.getBuffer(), f1.getBuffer());
	}

	@Test
	public void testEncodingAndDecodingWithMissingSegment() throws Exception {
		XorErasureEncoder e = new XorErasureEncoder(5);
		XorErasureDecoder d = new XorErasureDecoder(5);
		Frame f = new Frame(1234);
		new Random().nextBytes(f.getBuffer());
		int payload = 1234 - FRAME_HEADER_LENGTH - MAC_LENGTH;
		HeaderEncoder.encodeHeader(f.getBuffer(), 0L, payload, 0);
		f.setLength(1234);
		for(int i = 0; i < 5; i++) {
			Segment[] set = e.encodeFrame(f);
			assertEquals(5, set.length);
			set[i] = null;
			Frame f1 = new Frame(1234);
			assertTrue(d.decodeFrame(f1, set));
			assertArrayEquals(f.getBuffer(), f1.getBuffer());
		}
	}
}
