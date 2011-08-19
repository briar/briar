package net.sf.briar.transport;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.crypto.Mac;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
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

	public ConnectionReaderImplTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		mac = crypto.getMac();
		mac.init(crypto.generateSecretKey());
	}

	// FIXME: Test corner cases and corrupt frames

	@Test
	public void testSingleByteFrame() throws Exception {
		// Six bytes for the header, one for the payload
		byte[] frame = new byte[6 + 1 + mac.getMacLength()];
		ByteUtils.writeUint16(1, frame, 4); // Payload length = 1
		// Calculate the MAC
		mac.update(frame, 0, 6 + 1);
		mac.doFinal(frame, 6 + 1);
		// Read the frame
		ByteArrayInputStream in = new ByteArrayInputStream(frame);
		ConnectionDecrypter d = new NullConnectionDecrypter(in);
		ConnectionReader r = new ConnectionReaderImpl(d, mac);
		// There should be one byte available before EOF
		assertEquals(0, r.getInputStream().read());
		assertEquals(-1, r.getInputStream().read());
	}

	@Test
	public void testMultipleFrames() throws Exception {
		// First frame: 123-byte payload
		byte[] frame = new byte[6 + 123 + mac.getMacLength()];
		ByteUtils.writeUint16(123, frame, 4);
		mac.update(frame, 0, 6 + 123);
		mac.doFinal(frame, 6 + 123);
		// Second frame: 1234-byte payload
		byte[] frame1 = new byte[6 + 1234 + mac.getMacLength()];
		ByteUtils.writeUint32(1, frame1, 0);
		ByteUtils.writeUint16(1234, frame1, 4);
		mac.update(frame1, 0, 6 + 1234);
		mac.doFinal(frame1, 6 + 1234);
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
