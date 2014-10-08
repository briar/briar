package org.briarproject.transport;

import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;

import org.briarproject.BriarTestCase;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public class StreamWriterImplTest extends BriarTestCase {

	private static final int FRAME_LENGTH = 1024;
	private static final int MAX_PAYLOAD_LENGTH =
			FRAME_LENGTH - HEADER_LENGTH - MAC_LENGTH;

	@Test
	public void testCloseWithoutWritingWritesFinalFrame() throws Exception {
		Mockery context = new Mockery();
		final FrameWriter writer = context.mock(FrameWriter.class);
		context.checking(new Expectations() {{
			// Write an empty final frame
			oneOf(writer).writeFrame(with(any(byte[].class)), with(0),
					with(true));
			// Flush the stream
			oneOf(writer).flush();
		}});
		StreamWriterImpl w = new StreamWriterImpl(writer, FRAME_LENGTH);
		w.close();
		context.assertIsSatisfied();
	}

	@Test
	public void testFlushWithoutBufferedDataWritesFrame() throws Exception {
		Mockery context = new Mockery();
		final FrameWriter writer = context.mock(FrameWriter.class);
		StreamWriterImpl w = new StreamWriterImpl(writer, FRAME_LENGTH);
		context.checking(new Expectations() {{
			// Flush the stream
			oneOf(writer).flush();
		}});
		w.flush();
		context.assertIsSatisfied();
	}

	@Test
	public void testFlushWithBufferedDataWritesFrameAndFlushes()
			throws Exception {
		Mockery context = new Mockery();
		final FrameWriter writer = context.mock(FrameWriter.class);
		StreamWriterImpl w = new StreamWriterImpl(writer, FRAME_LENGTH);
		context.checking(new Expectations() {{
			// Write a non-final frame with one payload byte
			oneOf(writer).writeFrame(with(any(byte[].class)), with(1),
					with(false));
			// Flush the stream
			oneOf(writer).flush();
		}});
		w.write(0);
		w.flush();
		context.assertIsSatisfied();
	}

	@Test
	public void testSingleByteWritesWriteFullFrame() throws Exception {
		Mockery context = new Mockery();
		final FrameWriter writer = context.mock(FrameWriter.class);
		StreamWriterImpl w = new StreamWriterImpl(writer, FRAME_LENGTH);
		context.checking(new Expectations() {{
			// Write a full non-final frame
			oneOf(writer).writeFrame(with(any(byte[].class)),
					with(MAX_PAYLOAD_LENGTH), with(false));
		}});
		for(int i = 0; i < MAX_PAYLOAD_LENGTH; i++) {
			w.write(0);
		}
		context.assertIsSatisfied();
	}

	@Test
	public void testMultiByteWritesWriteFullFrames() throws Exception {
		Mockery context = new Mockery();
		final FrameWriter writer = context.mock(FrameWriter.class);
		StreamWriterImpl w = new StreamWriterImpl(writer, FRAME_LENGTH);
		context.checking(new Expectations() {{
			// Write two full non-final frames
			exactly(2).of(writer).writeFrame(with(any(byte[].class)),
					with(MAX_PAYLOAD_LENGTH), with(false));
		}});
		// Sanity check
		assertEquals(0, MAX_PAYLOAD_LENGTH % 2);
		// Write two full payloads using four multi-byte writes
		byte[] b = new byte[MAX_PAYLOAD_LENGTH / 2];
		w.write(b);
		w.write(b);
		w.write(b);
		w.write(b);
		context.assertIsSatisfied();
	}

	@Test
	public void testLargeMultiByteWriteWritesFullFrames() throws Exception {
		Mockery context = new Mockery();
		final FrameWriter writer = context.mock(FrameWriter.class);
		StreamWriterImpl w = new StreamWriterImpl(writer, FRAME_LENGTH);
		context.checking(new Expectations() {{
			// Write two full non-final frames
			exactly(2).of(writer).writeFrame(with(any(byte[].class)),
					with(MAX_PAYLOAD_LENGTH), with(false));
			// Write a final frame with a one-byte payload
			oneOf(writer).writeFrame(with(any(byte[].class)), with(1),
					with(true));
			// Flush the stream
			oneOf(writer).flush();
		}});
		// Write two full payloads using one large multi-byte write
		byte[] b = new byte[MAX_PAYLOAD_LENGTH * 2 + 1];
		w.write(b);
		// There should be one byte left in the buffer
		w.close();
		context.assertIsSatisfied();
	}
}
