package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import net.sf.briar.BriarTestCase;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.transport.Segment;

import static org.junit.Assert.assertArrayEquals;
import org.junit.Test;

public class XorErasureDecoderTest extends BriarTestCase {

	@Test
	public void testMaximumLength() throws Exception {
		// A frame of the maximum length should be decoded successfully
		Segment[] set = encodeEmptyFrame(MAX_FRAME_LENGTH / 4, 5);
		XorErasureDecoder d = new XorErasureDecoder(5);
		Frame f = new Frame();
		assertTrue(d.decodeFrame(f, set));
		// Check the header
		byte[] b = f.getBuffer();
		assertEquals(0L, HeaderEncoder.getFrameNumber(b));
		int payload = MAX_FRAME_LENGTH - FRAME_HEADER_LENGTH - MAC_LENGTH;
		assertEquals(payload, HeaderEncoder.getPayloadLength(b));
		assertEquals(0, HeaderEncoder.getPaddingLength(b));
		// Check the body
		assertEquals(MAX_FRAME_LENGTH, f.getLength());
		for(int i = FRAME_HEADER_LENGTH; i < MAX_FRAME_LENGTH; i++) {
			assertEquals(0, b[i]);
		}
		// A frame larger than the maximum length should not be decoded
		set = encodeEmptyFrame(MAX_FRAME_LENGTH / 4 + 1, 5);
		try {
			d.decodeFrame(f, set);
		} catch(FormatException expected) {}
	}

	@Test
	public void testMinimumLengthIsUsed() throws Exception {
		Segment[] set = encodeEmptyFrame(250, 4);
		// Replace one of the pieces with a longer piece
		byte[] b = set[1].getBuffer();
		assertArrayEquals(new byte[250], b);
		set[1] = new SegmentImpl(251);
		set[1].setLength(251);
		// The frame should be decoded successfully
		XorErasureDecoder d = new XorErasureDecoder(4);
		Frame f = new Frame();
		assertTrue(d.decodeFrame(f, set));
		// The minimum of the segments' lengths should have been used
		assertEquals(750, f.getLength());
	}

	private Segment[] encodeEmptyFrame(int length, int n) {
		Segment[] set = new Segment[n];
		for(int i = 0; i < n; i++) {
			set[i] = new SegmentImpl(length);
			set[i].setLength(length);
		}
		int payload = length * (n - 1) - FRAME_HEADER_LENGTH - MAC_LENGTH;
		HeaderEncoder.encodeHeader(set[0].getBuffer(), 0L, payload, 0);
		HeaderEncoder.encodeHeader(set[n - 1].getBuffer(), 0L, payload, 0);
		return set;
	}
}
