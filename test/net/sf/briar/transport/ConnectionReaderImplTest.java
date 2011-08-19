package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.crypto.Mac;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.util.ByteUtils;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConnectionReaderImplTest extends TestCase {

	private final Mac mac;
	private final int headerLength = 8, macLength;

	public ConnectionReaderImplTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		mac = crypto.getMac();
		mac.init(crypto.generateSecretKey());
		macLength = mac.getMacLength();
	}

	@Test
	public void testLengthZero() throws Exception {
		int payloadLength = 0;
		byte[] frame = new byte[headerLength + payloadLength + macLength];
		writeHeader(frame, 0L, payloadLength, 0);
		// Calculate the MAC
		mac.update(frame, 0, headerLength + payloadLength);
		mac.doFinal(frame, headerLength + payloadLength);
		// Read the frame
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		ConnectionDecrypter d = new NullConnectionDecrypter(in);
		ConnectionReader r = new ConnectionReaderImpl(d, mac);
		try {
			r.getInputStream().read();
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testLengthOne() throws Exception {
		int payloadLength = 1;
		byte[] frame = new byte[headerLength + payloadLength + macLength];
		writeHeader(frame, 0L, payloadLength, 0);
		// Calculate the MAC
		mac.update(frame, 0, headerLength + payloadLength);
		mac.doFinal(frame, headerLength + payloadLength);
		// Read the frame
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		ConnectionDecrypter d = new NullConnectionDecrypter(in);
		ConnectionReader r = new ConnectionReaderImpl(d, mac);
		// There should be one byte available before EOF
		assertEquals(0, r.getInputStream().read());
		assertEquals(-1, r.getInputStream().read());
	}

	@Test
	public void testMaxLength() throws Exception {
		int maxPayloadLength = MAX_FRAME_LENGTH - headerLength - macLength;
		// First frame: max payload length
		byte[] frame = new byte[MAX_FRAME_LENGTH];
		writeHeader(frame, 0L, maxPayloadLength, 0);
		mac.update(frame, 0, headerLength + maxPayloadLength);
		mac.doFinal(frame, headerLength + maxPayloadLength);
		// Second frame: max payload length plus one
		byte[] frame1 = new byte[MAX_FRAME_LENGTH + 1];
		writeHeader(frame1, 1L, maxPayloadLength + 1, 0);
		mac.update(frame1, 0, headerLength + maxPayloadLength + 1);
		mac.doFinal(frame1, headerLength + maxPayloadLength + 1);
		// Concatenate the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(frame);
		out.write(frame1);
		// Read the first frame
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		ConnectionDecrypter d = new NullConnectionDecrypter(in);
		ConnectionReader r = new ConnectionReaderImpl(d, mac);
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
		int maxPayloadLength = MAX_FRAME_LENGTH - headerLength - macLength;
		int paddingLength = 10;
		// First frame: max payload length, including padding
		byte[] frame = new byte[MAX_FRAME_LENGTH];
		writeHeader(frame, 0L, maxPayloadLength - paddingLength, paddingLength);
		mac.update(frame, 0, headerLength + maxPayloadLength);
		mac.doFinal(frame, headerLength + maxPayloadLength);
		// Second frame: max payload length plus one, including padding
		byte[] frame1 = new byte[MAX_FRAME_LENGTH + 1];
		writeHeader(frame1, 1L, maxPayloadLength + 1 - paddingLength,
				paddingLength);
		mac.update(frame1, 0, headerLength + maxPayloadLength + 1);
		mac.doFinal(frame1, headerLength + maxPayloadLength + 1);
		// Concatenate the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(frame);
		out.write(frame1);
		// Read the first frame
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		ConnectionDecrypter d = new NullConnectionDecrypter(in);
		ConnectionReader r = new ConnectionReaderImpl(d, mac);
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
		byte[] frame = new byte[8 + 123 + mac.getMacLength()];
		writeHeader(frame, 0L, 123, 0);
		mac.update(frame, 0, 8 + 123);
		mac.doFinal(frame, 8 + 123);
		// Second frame: 1234-byte payload
		byte[] frame1 = new byte[8 + 1234 + mac.getMacLength()];
		writeHeader(frame1, 1L, 1234, 0);
		mac.update(frame1, 0, 8 + 1234);
		mac.doFinal(frame1, 8 + 1234);
		// Concatenate the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(frame);
		out.write(frame1);
		// Read the frames
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		ConnectionDecrypter d = new NullConnectionDecrypter(in);
		ConnectionReader r = new ConnectionReaderImpl(d, mac);
		byte[] read = new byte[123];
		TestUtils.readFully(r.getInputStream(), read);
		assertTrue(Arrays.equals(new byte[123], read));
		byte[] read1 = new byte[1234];
		TestUtils.readFully(r.getInputStream(), read1);
		assertTrue(Arrays.equals(new byte[1234], read1));
	}

	@Test
	public void testCorruptPayload() throws Exception {
		int payloadLength = 8;
		byte[] frame = new byte[headerLength + payloadLength + macLength];
		writeHeader(frame, 0L, payloadLength, 0);
		// Calculate the MAC
		mac.update(frame, 0, headerLength + payloadLength);
		mac.doFinal(frame, headerLength + payloadLength);
		// Modify the payload
		frame[12] ^= 1;
		// Try to read the frame - not a single byte should be read
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		ConnectionDecrypter d = new NullConnectionDecrypter(in);
		ConnectionReader r = new ConnectionReaderImpl(d, mac);
		try {
			r.getInputStream().read();
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testCorruptMac() throws Exception {
		int payloadLength = 8;
		byte[] frame = new byte[headerLength + payloadLength + macLength];
		writeHeader(frame, 0L, payloadLength, 0);
		// Calculate the MAC
		mac.update(frame, 0, headerLength + payloadLength);
		mac.doFinal(frame, headerLength + payloadLength);
		// Modify the MAC
		frame[17] ^= 1;
		// Try to read the frame - not a single byte should be read
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		ConnectionDecrypter d = new NullConnectionDecrypter(in);
		ConnectionReader r = new ConnectionReaderImpl(d, mac);
		try {
			r.getInputStream().read();
			fail();
		} catch(FormatException expected) {}
	}

	private void writeHeader(byte[] b, long frame, int payload, int padding) {
		ByteUtils.writeUint32(frame, b, 0);
		ByteUtils.writeUint16(payload, b, 4);
		ByteUtils.writeUint16(padding, b, 6);
	}

	/** A ConnectionDecrypter that performs no decryption. */
	private static class NullConnectionDecrypter
	implements ConnectionDecrypter {

		private final InputStream in;

		private NullConnectionDecrypter(InputStream in) {
			this.in = in;
		}

		public InputStream getInputStream() {
			return in;
		}

		public void readMac(byte[] mac) throws IOException {
			int offset = 0;
			while(offset < mac.length) {
				int read = in.read(mac, offset, mac.length - offset);
				if(read == -1) break;
				offset += read;
			}
			if(offset < mac.length) throw new EOFException();
		}
	}
}
