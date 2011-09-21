package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Arrays;

import net.sf.briar.api.transport.ConnectionWriter;

import org.junit.Test;

public class ConnectionWriterImplTest extends TransportTest {

	public ConnectionWriterImplTest() throws Exception {
		super();
	}

	@Test
	public void testFlushWithoutWriteProducesNothing() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter e = new NullConnectionEncrypter(out);
		ConnectionWriter w = new ConnectionWriterImpl(e, mac);
		w.getOutputStream().flush();
		w.getOutputStream().flush();
		w.getOutputStream().flush();
		assertEquals(0, out.size());
	}

	@Test
	public void testSingleByteFrame() throws Exception {
		int payloadLength = 1;
		byte[] frame = new byte[headerLength + payloadLength + macLength];
		writeHeader(frame, payloadLength, 0);
		// Calculate the MAC
		mac.update(frame, 0, headerLength + payloadLength);
		mac.doFinal(frame, headerLength + payloadLength);
		// Check that the ConnectionWriter gets the same results
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter e = new NullConnectionEncrypter(out);
		ConnectionWriter w = new ConnectionWriterImpl(e, mac);
		w.getOutputStream().write(0);
		w.getOutputStream().flush();
		assertTrue(Arrays.equals(frame, out.toByteArray()));
	}

	@Test
	public void testWriteByteToMaxLengthWritesFrame() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter e = new NullConnectionEncrypter(out);
		ConnectionWriter w = new ConnectionWriterImpl(e, mac);
		OutputStream out1 = w.getOutputStream();
		// The first maxPayloadLength - 1 bytes should be buffered
		for(int i = 0; i < maxPayloadLength - 1; i++) out1.write(0);
		assertEquals(0, out.size());
		// The next byte should trigger the writing of a frame
		out1.write(0);
		assertEquals(MAX_FRAME_LENGTH, out.size());
	}

	@Test
	public void testWriteArrayToMaxLengthWritesFrame() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter e = new NullConnectionEncrypter(out);
		ConnectionWriter w = new ConnectionWriterImpl(e, mac);
		OutputStream out1 = w.getOutputStream();
		// The first maxPayloadLength - 1 bytes should be buffered
		out1.write(new byte[maxPayloadLength - 1]);
		assertEquals(0, out.size());
		// The next maxPayloadLength + 1 bytes should trigger two frames
		out1.write(new byte[maxPayloadLength + 1]);
		assertEquals(MAX_FRAME_LENGTH * 2, out.size());
	}

	@Test
	public void testMultipleFrames() throws Exception {
		// First frame: 123-byte payload
		byte[] frame = new byte[headerLength + 123 + macLength];
		writeHeader(frame, 123, 0);
		mac.update(frame, 0, headerLength + 123);
		mac.doFinal(frame, headerLength + 123);
		// Second frame: 1234-byte payload
		byte[] frame1 = new byte[headerLength + 1234 + macLength];
		writeHeader(frame1, 1234, 0);
		mac.update(frame1, 0, headerLength + 1234);
		mac.doFinal(frame1, headerLength + 1234);
		// Concatenate the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(frame);
		out.write(frame1);
		byte[] expected = out.toByteArray();
		// Check that the ConnectionWriter gets the same results
		out.reset();
		ConnectionEncrypter e = new NullConnectionEncrypter(out);
		ConnectionWriter w = new ConnectionWriterImpl(e, mac);
		w.getOutputStream().write(new byte[123]);
		w.getOutputStream().flush();
		w.getOutputStream().write(new byte[1234]);
		w.getOutputStream().flush();
		byte[] actual = out.toByteArray();
		assertTrue(Arrays.equals(expected, actual));
	}

	@Test
	public void testGetCapacity() throws Exception {
		int overheadPerFrame = 4 + mac.getMacLength();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter e = new NullConnectionEncrypter(out);
		ConnectionWriterImpl w = new ConnectionWriterImpl(e, mac);
		// Full frame
		long capacity = w.getCapacity(MAX_FRAME_LENGTH);
		assertEquals(MAX_FRAME_LENGTH - overheadPerFrame, capacity);
		// Partial frame
		capacity = w.getCapacity(overheadPerFrame + 1);
		assertEquals(1, capacity);
		// Full frame and partial frame
		capacity = w.getCapacity(MAX_FRAME_LENGTH + 1);
		assertEquals(MAX_FRAME_LENGTH + 1 - 2 * overheadPerFrame, capacity);
		// Buffer some output
		w.getOutputStream().write(0);
		// Full frame minus buffered frame
		capacity = w.getCapacity(MAX_FRAME_LENGTH);
		assertEquals(MAX_FRAME_LENGTH - 1 - 2 * overheadPerFrame, capacity);
		// Flush the buffer
		w.flush();
		assertEquals(1 + overheadPerFrame, out.size());
		// Back to square one
		capacity = w.getCapacity(MAX_FRAME_LENGTH);
		assertEquals(MAX_FRAME_LENGTH - overheadPerFrame, capacity);
	}
}
