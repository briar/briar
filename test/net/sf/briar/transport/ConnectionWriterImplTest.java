package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import net.sf.briar.api.transport.ConnectionWriter;

import org.junit.Test;

// FIXME: This test covers too many classes
public class ConnectionWriterImplTest extends TransportTest {

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
		int payloadLength = 1;
		byte[] frame = new byte[FRAME_HEADER_LENGTH + payloadLength
		                        + MAC_LENGTH];
		HeaderEncoder.encodeHeader(frame, 0, payloadLength, 0);
		// Calculate the MAC
		mac.init(macKey);
		mac.update(frame, 0, FRAME_HEADER_LENGTH + payloadLength);
		mac.doFinal(frame, FRAME_HEADER_LENGTH + payloadLength);
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
		int payloadLength = 123;
		byte[] frame = new byte[FRAME_HEADER_LENGTH + payloadLength
		                        + MAC_LENGTH];
		HeaderEncoder.encodeHeader(frame, 0, payloadLength, 0);
		mac.init(macKey);
		mac.update(frame, 0, FRAME_HEADER_LENGTH + payloadLength);
		mac.doFinal(frame, FRAME_HEADER_LENGTH + payloadLength);
		// Second frame: 1234-byte payload
		int payloadLength1 = 1234;
		byte[] frame1 = new byte[FRAME_HEADER_LENGTH + payloadLength1
		                         + MAC_LENGTH];
		HeaderEncoder.encodeHeader(frame1, 1, payloadLength1, 0);
		mac.update(frame1, 0, FRAME_HEADER_LENGTH + 1234);
		mac.doFinal(frame1, FRAME_HEADER_LENGTH + 1234);
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
		OutgoingEncryptionLayer encryption =
			new NullOutgoingEncryptionLayer(out);
		OutgoingErrorCorrectionLayer correction =
			new NullOutgoingErrorCorrectionLayer(encryption);
		OutgoingAuthenticationLayer authentication =
			new OutgoingAuthenticationLayerImpl(correction, mac, macKey);
		OutgoingReliabilityLayer reliability =
			new NullOutgoingReliabilityLayer(authentication);
		return new ConnectionWriterImpl(reliability, false);
	}
}
