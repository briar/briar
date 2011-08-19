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
		writeHeader(frame, 0L, payloadLength, 0);
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
	public void testFrameIsWrittenAtMaxLength() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter e = new NullConnectionEncrypter(out);
		ConnectionWriter w = new ConnectionWriterImpl(e, mac);
		OutputStream out1 = w.getOutputStream();
		// The first maxPayloadLength bytes should be buffered
		for(int i = 0; i < maxPayloadLength; i++) out1.write(0);
		assertEquals(0, out.size());
		// The next byte should trigger the writing of a frame
		out1.write(0);
		assertEquals(MAX_FRAME_LENGTH, out.size());
		// Flushing the stream should write a single-byte frame
		out1.flush();
		assertEquals(MAX_FRAME_LENGTH + headerLength + 1 + macLength,
				out.size());
	}

	@Test
	public void testMultipleFrames() throws Exception {
		// First frame: 123-byte payload
		byte[] frame = new byte[headerLength + 123 + macLength];
		writeHeader(frame, 0L, 123, 0);
		mac.update(frame, 0, headerLength + 123);
		mac.doFinal(frame, headerLength + 123);
		// Second frame: 1234-byte payload
		byte[] frame1 = new byte[headerLength + 1234 + macLength];
		writeHeader(frame1, 1L, 1234, 0);
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
}
