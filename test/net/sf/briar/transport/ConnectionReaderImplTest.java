package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;

import net.sf.briar.TestUtils;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.transport.ConnectionReader;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Test;

public class ConnectionReaderImplTest extends TransportTest {

	public ConnectionReaderImplTest() throws Exception {
		super();
	}

	@Test
	public void testLengthZero() throws Exception {
		int payloadLength = 0;
		byte[] frame = new byte[FRAME_HEADER_LENGTH + payloadLength
		                        + macLength];
		HeaderEncoder.encodeHeader(frame, 0, payloadLength, 0);
		// Calculate the MAC
		mac.init(macKey);
		mac.update(frame, 0, FRAME_HEADER_LENGTH + payloadLength);
		mac.doFinal(frame, FRAME_HEADER_LENGTH + payloadLength);
		// Read the frame
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		ConnectionDecrypter d = new NullConnectionDecrypter(in);
		ConnectionReader r = new ConnectionReaderImpl(d, mac, macKey);
		try {
			r.getInputStream().read();
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testLengthOne() throws Exception {
		int payloadLength = 1;
		byte[] frame = new byte[FRAME_HEADER_LENGTH + payloadLength
		                        + macLength];
		HeaderEncoder.encodeHeader(frame, 0, payloadLength, 0);
		// Calculate the MAC
		mac.init(macKey);
		mac.update(frame, 0, FRAME_HEADER_LENGTH + payloadLength);
		mac.doFinal(frame, FRAME_HEADER_LENGTH + payloadLength);
		// Read the frame
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		ConnectionDecrypter d = new NullConnectionDecrypter(in);
		ConnectionReader r = new ConnectionReaderImpl(d, mac, macKey);
		// There should be one byte available before EOF
		assertEquals(0, r.getInputStream().read());
		assertEquals(-1, r.getInputStream().read());
	}

	@Test
	public void testMaxLength() throws Exception {
		// First frame: max payload length
		byte[] frame = new byte[MAX_FRAME_LENGTH];
		HeaderEncoder.encodeHeader(frame, 0, maxPayloadLength, 0);
		mac.init(macKey);
		mac.update(frame, 0, FRAME_HEADER_LENGTH + maxPayloadLength);
		mac.doFinal(frame, FRAME_HEADER_LENGTH + maxPayloadLength);
		// Second frame: max payload length plus one
		byte[] frame1 = new byte[MAX_FRAME_LENGTH + 1];
		HeaderEncoder.encodeHeader(frame1, 1, maxPayloadLength + 1, 0);
		mac.update(frame1, 0, FRAME_HEADER_LENGTH + maxPayloadLength + 1);
		mac.doFinal(frame1, FRAME_HEADER_LENGTH + maxPayloadLength + 1);
		// Concatenate the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(frame);
		out.write(frame1);
		// Read the first frame
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		ConnectionDecrypter d = new NullConnectionDecrypter(in);
		ConnectionReader r = new ConnectionReaderImpl(d, mac, macKey);
		byte[] read = new byte[maxPayloadLength];
		TestUtils.readFully(r.getInputStream(), read);
		// Try to read the second frame
		byte[] read1 = new byte[maxPayloadLength + 1];
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
		HeaderEncoder.encodeHeader(frame, 0, maxPayloadLength - paddingLength,
				paddingLength);
		mac.init(macKey);
		mac.update(frame, 0, FRAME_HEADER_LENGTH + maxPayloadLength);
		mac.doFinal(frame, FRAME_HEADER_LENGTH + maxPayloadLength);
		// Second frame: max payload length plus one, including padding
		byte[] frame1 = new byte[MAX_FRAME_LENGTH + 1];
		HeaderEncoder.encodeHeader(frame1, 1,
				maxPayloadLength + 1 - paddingLength, paddingLength);
		mac.update(frame1, 0, FRAME_HEADER_LENGTH + maxPayloadLength + 1);
		mac.doFinal(frame1, FRAME_HEADER_LENGTH + maxPayloadLength + 1);
		// Concatenate the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(frame);
		out.write(frame1);
		// Read the first frame
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		ConnectionDecrypter d = new NullConnectionDecrypter(in);
		ConnectionReader r = new ConnectionReaderImpl(d, mac, macKey);
		byte[] read = new byte[maxPayloadLength - paddingLength];
		TestUtils.readFully(r.getInputStream(), read);
		// Try to read the second frame
		byte[] read1 = new byte[maxPayloadLength + 1 - paddingLength];
		try {
			TestUtils.readFully(r.getInputStream(), read1);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testMultipleFrames() throws Exception {
		// First frame: 123-byte payload
		int payloadLength = 123;
		byte[] frame = new byte[FRAME_HEADER_LENGTH + payloadLength
		                        + macLength];
		HeaderEncoder.encodeHeader(frame, 0, payloadLength, 0);
		mac.init(macKey);
		mac.update(frame, 0, FRAME_HEADER_LENGTH + payloadLength);
		mac.doFinal(frame, FRAME_HEADER_LENGTH + payloadLength);
		// Second frame: 1234-byte payload
		int payloadLength1 = 1234;
		byte[] frame1 = new byte[FRAME_HEADER_LENGTH + payloadLength1
		                         + macLength];
		HeaderEncoder.encodeHeader(frame1, 1, payloadLength1, 0);
		mac.update(frame1, 0, FRAME_HEADER_LENGTH + payloadLength1);
		mac.doFinal(frame1, FRAME_HEADER_LENGTH + payloadLength1);
		// Concatenate the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(frame);
		out.write(frame1);
		// Read the frames
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		ConnectionDecrypter d = new NullConnectionDecrypter(in);
		ConnectionReader r = new ConnectionReaderImpl(d, mac, macKey);
		byte[] read = new byte[payloadLength];
		TestUtils.readFully(r.getInputStream(), read);
		assertArrayEquals(new byte[payloadLength], read);
		byte[] read1 = new byte[payloadLength1];
		TestUtils.readFully(r.getInputStream(), read1);
		assertArrayEquals(new byte[payloadLength1], read1);
	}

	@Test
	public void testCorruptPayload() throws Exception {
		int payloadLength = 8;
		byte[] frame = new byte[FRAME_HEADER_LENGTH + payloadLength
		                        + macLength];
		HeaderEncoder.encodeHeader(frame, 0, payloadLength, 0);
		// Calculate the MAC
		mac.init(macKey);
		mac.update(frame, 0, FRAME_HEADER_LENGTH + payloadLength);
		mac.doFinal(frame, FRAME_HEADER_LENGTH + payloadLength);
		// Modify the payload
		frame[12] ^= 1;
		// Try to read the frame - not a single byte should be read
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		ConnectionDecrypter d = new NullConnectionDecrypter(in);
		ConnectionReader r = new ConnectionReaderImpl(d, mac, macKey);
		try {
			r.getInputStream().read();
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testCorruptMac() throws Exception {
		int payloadLength = 8;
		byte[] frame = new byte[FRAME_HEADER_LENGTH + payloadLength
		                        + macLength];
		HeaderEncoder.encodeHeader(frame, 0, payloadLength, 0);
		// Calculate the MAC
		mac.init(macKey);
		mac.update(frame, 0, FRAME_HEADER_LENGTH + payloadLength);
		mac.doFinal(frame, FRAME_HEADER_LENGTH + payloadLength);
		// Modify the MAC
		frame[17] ^= 1;
		// Try to read the frame - not a single byte should be read
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		ConnectionDecrypter d = new NullConnectionDecrypter(in);
		ConnectionReader r = new ConnectionReaderImpl(d, mac, macKey);
		try {
			r.getInputStream().read();
			fail();
		} catch(FormatException expected) {}
	}
}
