package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.transport.ConnectionReader;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Test;

public class ConnectionReaderImplTest extends BriarTestCase {

	private static final int MAX_PAYLOAD_LENGTH =
			MAX_FRAME_LENGTH - HEADER_LENGTH - MAC_LENGTH;

	public ConnectionReaderImplTest() throws Exception {
		super();
	}

	@Test
	public void testLengthZero() throws Exception {
		byte[] frame = new byte[HEADER_LENGTH + MAC_LENGTH];
		HeaderEncoder.encodeHeader(frame, 0, 0, 0, true);
		// Read the frame
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		ConnectionReader r = createConnectionReader(in);
		// There should be no bytes available before EOF
		assertEquals(-1, r.getInputStream().read());
	}

	@Test
	public void testLengthOne() throws Exception {
		byte[] frame = new byte[HEADER_LENGTH + 1 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(frame, 0, 1, 0, true);
		// Read the frame
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		ConnectionReader r = createConnectionReader(in);
		// There should be one byte available before EOF
		assertEquals(0, r.getInputStream().read());
		assertEquals(-1, r.getInputStream().read());
	}

	@Test
	public void testMaxLength() throws Exception {
		// First frame: max payload length
		byte[] frame = new byte[MAX_FRAME_LENGTH];
		HeaderEncoder.encodeHeader(frame, 0, MAX_PAYLOAD_LENGTH, 0, false);
		// Second frame: max payload length plus one
		byte[] frame1 = new byte[MAX_FRAME_LENGTH + 1];
		HeaderEncoder.encodeHeader(frame1, 1, MAX_PAYLOAD_LENGTH + 1, 0, false);
		// Concatenate the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(frame);
		out.write(frame1);
		// Read the first frame
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		ConnectionReader r = createConnectionReader(in);
		byte[] read = new byte[MAX_PAYLOAD_LENGTH];
		TestUtils.readFully(r.getInputStream(), read);
		// Try to read the second frame
		byte[] read1 = new byte[MAX_PAYLOAD_LENGTH + 1];
		try {
			TestUtils.readFully(r.getInputStream(), read1);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testMaxLengthWithPadding() throws Exception {
		int paddingLength = 10;
		// First frame: max payload length, including padding
		byte[] frame = new byte[MAX_FRAME_LENGTH];
		HeaderEncoder.encodeHeader(frame, 0, MAX_PAYLOAD_LENGTH - paddingLength,
				paddingLength, false);
		// Second frame: max payload length plus one, including padding
		byte[] frame1 = new byte[MAX_FRAME_LENGTH + 1];
		HeaderEncoder.encodeHeader(frame1, 1,
				MAX_PAYLOAD_LENGTH + 1 - paddingLength, paddingLength, false);
		// Concatenate the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(frame);
		out.write(frame1);
		// Read the first frame
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		ConnectionReader r = createConnectionReader(in);
		byte[] read = new byte[MAX_PAYLOAD_LENGTH - paddingLength];
		TestUtils.readFully(r.getInputStream(), read);
		// Try to read the second frame
		byte[] read1 = new byte[MAX_PAYLOAD_LENGTH + 1 - paddingLength];
		try {
			TestUtils.readFully(r.getInputStream(), read1);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testNonZeroPadding() throws Exception {
		int payloadLength = 10, paddingLength = 10;
		byte[] frame = new byte[HEADER_LENGTH + payloadLength + paddingLength
		                        + MAC_LENGTH];
		HeaderEncoder.encodeHeader(frame, 0, payloadLength, paddingLength,
				false);
		// Set a byte of the padding to a non-zero value
		frame[HEADER_LENGTH + payloadLength] = 1;
		// Read the frame
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		ConnectionReader r = createConnectionReader(in);
		// The non-zero padding should be rejected
		try {
			r.getInputStream().read();
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testMultipleFrames() throws Exception {
		// First frame: 123-byte payload
		int payloadLength = 123;
		byte[] frame = new byte[HEADER_LENGTH + payloadLength + MAC_LENGTH];
		HeaderEncoder.encodeHeader(frame, 0, payloadLength, 0, false);
		// Second frame: 1234-byte payload
		int payloadLength1 = 1234;
		byte[] frame1 = new byte[HEADER_LENGTH + payloadLength1 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(frame1, 1, payloadLength1, 0, true);
		// Concatenate the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(frame);
		out.write(frame1);
		// Read the frames
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		ConnectionReader r = createConnectionReader(in);
		byte[] read = new byte[payloadLength];
		TestUtils.readFully(r.getInputStream(), read);
		assertArrayEquals(new byte[payloadLength], read);
		byte[] read1 = new byte[payloadLength1];
		TestUtils.readFully(r.getInputStream(), read1);
		assertArrayEquals(new byte[payloadLength1], read1);
		assertEquals(-1, r.getInputStream().read());		
	}

	@Test
	public void testLastFrameNotMarkedAsSuch() throws Exception {
		// First frame: 123-byte payload
		int payloadLength = 123;
		byte[] frame = new byte[HEADER_LENGTH + payloadLength + MAC_LENGTH];
		HeaderEncoder.encodeHeader(frame, 0, payloadLength, 0, false);
		// Second frame: 1234-byte payload
		int payloadLength1 = 1234;
		byte[] frame1 = new byte[HEADER_LENGTH + payloadLength1 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(frame1, 1, payloadLength1, 0, false);
		// Concatenate the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(frame);
		out.write(frame1);
		// Read the frames
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		ConnectionReader r = createConnectionReader(in);
		byte[] read = new byte[payloadLength];
		TestUtils.readFully(r.getInputStream(), read);
		assertArrayEquals(new byte[payloadLength], read);
		byte[] read1 = new byte[payloadLength1];
		TestUtils.readFully(r.getInputStream(), read1);
		assertArrayEquals(new byte[payloadLength1], read1);
		try {
			r.getInputStream().read();
			fail();
		} catch(FormatException expected) {}
	}

	private ConnectionReader createConnectionReader(InputStream in) {
		FrameReader encryption = new NullIncomingEncryptionLayer(in);
		return new ConnectionReaderImpl(encryption);
	}
}
