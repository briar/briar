package net.sf.briar.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import javax.crypto.Mac;

import junit.framework.TestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.util.ByteUtils;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConnectionWriterImplTest extends TestCase {

	private final Mac mac;

	public ConnectionWriterImplTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		mac = crypto.getMac();
		mac.init(crypto.generateSecretKey());
	}

	// FIXME: Test corner cases

	@Test
	public void testSingleByteFrame() throws Exception {
		// Six bytes for the header, one for the payload
		byte[] frame = new byte[6 + 1 + mac.getMacLength()];
		ByteUtils.writeUint16(1, frame, 4); // Payload length = 1
		// Calculate the MAC
		mac.update(frame, 0, 6 + 1);
		mac.doFinal(frame, 6 + 1);
		// Check that the ConnectionWriter gets the same results
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter e = new NullConnectionEncrypter(out);
		ConnectionWriter w = new ConnectionWriterImpl(e, mac);
		w.getOutputStream().write(0);
		w.getOutputStream().flush();
		assertTrue(Arrays.equals(frame, out.toByteArray()));
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

	/** A ConnectionEncrypter that performs no encryption. */
	private static class NullConnectionEncrypter
	implements ConnectionEncrypter {

		private final OutputStream out;

		private NullConnectionEncrypter(OutputStream out) {
			this.out = out;
		}

		public OutputStream getOutputStream() {
			return out;
		}

		public void writeMac(byte[] mac) throws IOException {
			out.write(mac);
		}
	}
}
