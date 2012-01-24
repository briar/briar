package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.junit.Assert.assertArrayEquals;
import net.sf.briar.BriarTestCase;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.transport.Segment;

import org.junit.Test;

public class XorErasureDecoderTest extends BriarTestCase {

	@Test
	public void testMaximumLength() throws Exception {
		XorErasureDecoder d = new XorErasureDecoder(5, false);
		// A frame of the maximum length should be decoded successfully
		Segment[] set = encodeEmptyFrame(MAX_FRAME_LENGTH / 4, 5);
		Frame f = new Frame();
		assertTrue(d.decodeFrame(f, set));
		checkFrame(f, MAX_FRAME_LENGTH);
		// A frame larger than the maximum length should not be decoded
		set = encodeEmptyFrame(MAX_FRAME_LENGTH / 4 + 1, 5);
		f = new Frame();
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
		XorErasureDecoder d = new XorErasureDecoder(4, false);
		Frame f = new Frame(750);
		assertTrue(d.decodeFrame(f, set));
		// The minimum of the segments' lengths should have been used
		assertEquals(750, f.getLength());
	}

	@Test
	public void testDecodingWithMissingSegment() throws Exception {
		XorErasureDecoder d = new XorErasureDecoder(4, false);
		for(int i = 0; i < 4; i++) {
			Segment[] set = encodeEmptyFrame(250, 4);
			set[i] = null;
			// The frame should be decoded successfully
			Frame f = new Frame(750);
			assertTrue(d.decodeFrame(f, set));
			checkFrame(f, 750);
		}
	}

	@Test
	public void testDecodingWithTwoMissingSegments() throws Exception {
		XorErasureDecoder d = new XorErasureDecoder(4, false);
		Segment[] set = encodeEmptyFrame(250, 4);
		set[0] = null;
		set[1] = null;
		Frame f = new Frame(750);
		assertFalse(d.decodeFrame(f, set));
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

	private void checkFrame(Frame f, int length) {
		byte[] b = f.getBuffer();
		assertEquals(0L, HeaderEncoder.getFrameNumber(b));
		int payload = length - FRAME_HEADER_LENGTH - MAC_LENGTH;
		assertEquals(payload, HeaderEncoder.getPayloadLength(b));
		assertEquals(0, HeaderEncoder.getPaddingLength(b));
		// Check the body
		assertEquals(length, f.getLength());
		for(int i = FRAME_HEADER_LENGTH; i < length; i++) {
			assertEquals("" + i, 0, b[i]);
		}
	}
}
