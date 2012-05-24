package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.transport.ConnectionWriter;

import org.junit.Test;

public class ConnectionWriterImplTest extends BriarTestCase {

	private static final int MAX_PAYLOAD_LENGTH =
			MAX_FRAME_LENGTH - HEADER_LENGTH - MAC_LENGTH;

	public ConnectionWriterImplTest() throws Exception {
		super();
	}

	@Test
	public void testFlushWithoutWriteProducesNothing() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionWriter w = createConnectionWriter(out);
		w.getOutputStream().flush();
		w.getOutputStream().flush();
		w.getOutputStream().flush();
		assertEquals(0, out.size());
	}

	@Test
	public void testSingleByteFrame() throws Exception {
		// Create a single-byte frame
		byte[] frame = new byte[HEADER_LENGTH + 1 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(frame, 0, 1, 0, false);
		// Check that the ConnectionWriter gets the same results
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionWriter w = createConnectionWriter(out);
		w.getOutputStream().write(0);
		w.getOutputStream().flush();
		assertArrayEquals(frame, out.toByteArray());
	}

	@Test
	public void testWriteByteToMaxLengthWritesFrame() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionWriter w = createConnectionWriter(out);
		OutputStream out1 = w.getOutputStream();
		// The first maxPayloadLength - 1 bytes should be buffered
		for(int i = 0; i < MAX_PAYLOAD_LENGTH - 1; i++) out1.write(0);
		assertEquals(0, out.size());
		// The next byte should trigger the writing of a frame
		out1.write(0);
		assertEquals(MAX_FRAME_LENGTH, out.size());
	}

	@Test
	public void testWriteArrayToMaxLengthWritesFrame() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionWriter w = createConnectionWriter(out);
		OutputStream out1 = w.getOutputStream();
		// The first maxPayloadLength - 1 bytes should be buffered
		out1.write(new byte[MAX_PAYLOAD_LENGTH - 1]);
		assertEquals(0, out.size());
		// The next maxPayloadLength + 1 bytes should trigger two frames
		out1.write(new byte[MAX_PAYLOAD_LENGTH + 1]);
		assertEquals(MAX_FRAME_LENGTH * 2, out.size());
	}

	@Test
	public void testMultipleFrames() throws Exception {
		// First frame: 123-byte payload
		byte[] frame = new byte[HEADER_LENGTH + 123 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(frame, 0, 123, 0, false);
		// Second frame: 1234-byte payload
		byte[] frame1 = new byte[HEADER_LENGTH + 1234 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(frame1, 1, 1234, 0, false);
		// Concatenate the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(frame);
		out.write(frame1);
		byte[] expected = out.toByteArray();
		// Check that the ConnectionWriter gets the same results
		out.reset();
		ConnectionWriter w = createConnectionWriter(out);
		w.getOutputStream().write(new byte[123]);
		w.getOutputStream().flush();
		w.getOutputStream().write(new byte[1234]);
		w.getOutputStream().flush();
		byte[] actual = out.toByteArray();
		assertArrayEquals(expected, actual);
	}

	private ConnectionWriter createConnectionWriter(OutputStream out) {
		FrameWriter encryption = new NullOutgoingEncryptionLayer(out);
		return new ConnectionWriterImpl(encryption);
	}
}
