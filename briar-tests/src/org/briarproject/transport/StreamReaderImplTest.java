package org.briarproject.transport;

import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;

import org.briarproject.BriarTestCase;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public class StreamReaderImplTest extends BriarTestCase {

	private static final int FRAME_LENGTH = 1024;
	private static final int MAX_PAYLOAD_LENGTH =
			FRAME_LENGTH - HEADER_LENGTH - MAC_LENGTH;

	@Test
	public void testEmptyFramesAreSkipped() throws Exception {
		Mockery context = new Mockery();
		final FrameReader reader = context.mock(FrameReader.class);
		context.checking(new Expectations() {{
			oneOf(reader).readFrame(with(any(byte[].class)));
			will(returnValue(0)); // Empty frame
			oneOf(reader).readFrame(with(any(byte[].class)));
			will(returnValue(2)); // Non-empty frame with two payload bytes
			oneOf(reader).readFrame(with(any(byte[].class)));
			will(returnValue(0)); // Empty frame
			oneOf(reader).readFrame(with(any(byte[].class)));
			will(returnValue(-1)); // No more frames
		}});
		StreamReaderImpl r = new StreamReaderImpl(reader, FRAME_LENGTH);
		assertEquals(0, r.read()); // Skip the first empty frame, read a byte
		assertEquals(0, r.read()); // Read another byte
		assertEquals(-1, r.read()); // Skip the second empty frame, reach EOF
		assertEquals(-1, r.read()); // Still at EOF
		context.assertIsSatisfied();
		r.close();
	}

	@Test
	public void testEmptyFramesAreSkippedWithBuffer() throws Exception {
		Mockery context = new Mockery();
		final FrameReader reader = context.mock(FrameReader.class);
		context.checking(new Expectations() {{
			oneOf(reader).readFrame(with(any(byte[].class)));
			will(returnValue(0)); // Empty frame
			oneOf(reader).readFrame(with(any(byte[].class)));
			will(returnValue(2)); // Non-empty frame with two payload bytes
			oneOf(reader).readFrame(with(any(byte[].class)));
			will(returnValue(0)); // Empty frame
			oneOf(reader).readFrame(with(any(byte[].class)));
			will(returnValue(-1)); // No more frames
		}});
		StreamReaderImpl r = new StreamReaderImpl(reader, FRAME_LENGTH);
		byte[] buf = new byte[MAX_PAYLOAD_LENGTH];
		// Skip the first empty frame, read the two payload bytes
		assertEquals(2, r.read(buf));
		// Skip the second empty frame, reach EOF
		assertEquals(-1, r.read(buf));
		// Still at EOF
		assertEquals(-1, r.read(buf));
		context.assertIsSatisfied();
		r.close();
	}

	@Test
	public void testMultipleReadsPerFrame() throws Exception {
		Mockery context = new Mockery();
		final FrameReader reader = context.mock(FrameReader.class);
		context.checking(new Expectations() {{
			oneOf(reader).readFrame(with(any(byte[].class)));
			will(returnValue(MAX_PAYLOAD_LENGTH)); // Nice long frame
			oneOf(reader).readFrame(with(any(byte[].class)));
			will(returnValue(-1)); // No more frames
		}});
		StreamReaderImpl r = new StreamReaderImpl(reader, FRAME_LENGTH);
		byte[] buf = new byte[MAX_PAYLOAD_LENGTH / 2];
		// Read the first half of the payload
		assertEquals(MAX_PAYLOAD_LENGTH / 2, r.read(buf));
		// Read the second half of the payload
		assertEquals(MAX_PAYLOAD_LENGTH / 2, r.read(buf));
		// Reach EOF
		assertEquals(-1, r.read(buf, 0, buf.length));
		context.assertIsSatisfied();
		r.close();
	}

	@Test
	public void testMultipleReadsPerFrameWithOffsets() throws Exception {
		Mockery context = new Mockery();
		final FrameReader reader = context.mock(FrameReader.class);
		context.checking(new Expectations() {{
			oneOf(reader).readFrame(with(any(byte[].class)));
			will(returnValue(MAX_PAYLOAD_LENGTH)); // Nice long frame
			oneOf(reader).readFrame(with(any(byte[].class)));
			will(returnValue(-1)); // No more frames
		}});
		StreamReaderImpl r = new StreamReaderImpl(reader, FRAME_LENGTH);
		byte[] buf = new byte[MAX_PAYLOAD_LENGTH];
		// Read the first half of the payload
		assertEquals(MAX_PAYLOAD_LENGTH / 2, r.read(buf, MAX_PAYLOAD_LENGTH / 2,
				MAX_PAYLOAD_LENGTH / 2));
		// Read the second half of the payload
		assertEquals(MAX_PAYLOAD_LENGTH / 2, r.read(buf, 123,
				MAX_PAYLOAD_LENGTH / 2));
		// Reach EOF
		assertEquals(-1, r.read(buf, 0, buf.length));
		context.assertIsSatisfied();
		r.close();
	}
}
