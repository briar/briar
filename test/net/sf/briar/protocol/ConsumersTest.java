package net.sf.briar.protocol;

import static org.junit.Assert.assertArrayEquals;

import java.security.GeneralSecurityException;
import java.util.Random;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.serial.CopyingConsumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.DigestingConsumer;

import org.junit.Test;

public class ConsumersTest extends BriarTestCase {

	@Test
	public void testDigestingConsumer() throws Exception {
		byte[] data = new byte[1234];
		// Generate some random data and digest it
		new Random().nextBytes(data);
		MessageDigest messageDigest = new TestMessageDigest();
		messageDigest.update(data);
		byte[] dig = messageDigest.digest();
		// Check that feeding a DigestingConsumer generates the same digest
		DigestingConsumer dc = new DigestingConsumer(messageDigest);
		dc.write(data[0]);
		dc.write(data, 1, data.length - 2);
		dc.write(data[data.length - 1]);
		byte[] dig1 = messageDigest.digest();
		assertArrayEquals(dig, dig1);
	}

	@Test
	public void testCountingConsumer() throws Exception {
		byte[] data = new byte[1234];
		CountingConsumer cc = new CountingConsumer(data.length);
		cc.write(data[0]);
		cc.write(data, 1, data.length - 2);
		cc.write(data[data.length - 1]);
		assertEquals(data.length, cc.getCount());
		try {
			cc.write((byte) 0);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testCopyingConsumer() throws Exception {
		byte[] data = new byte[1234];
		new Random().nextBytes(data);
		// Check that a CopyingConsumer creates a faithful copy
		CopyingConsumer cc = new CopyingConsumer();
		cc.write(data[0]);
		cc.write(data, 1, data.length - 2);
		cc.write(data[data.length - 1]);
		assertArrayEquals(data, cc.getCopy());
	}

	private static class TestMessageDigest implements MessageDigest {

		private final java.security.MessageDigest delegate;

		private TestMessageDigest() throws GeneralSecurityException {
			delegate = java.security.MessageDigest.getInstance("SHA-256");
		}

		public byte[] digest() {
			return delegate.digest();
		}

		public byte[] digest(byte[] input) {
			return delegate.digest(input);
		}

		public int digest(byte[] buf, int offset, int len) {
			byte[] digest = digest();
			len = Math.min(len, digest.length);
			System.arraycopy(digest, 0, buf, offset, len);
			return len;
		}

		public int getDigestLength() {
			return delegate.getDigestLength();
		}

		public void reset() {
			delegate.reset();
		}

		public void update(byte input) {
			delegate.update(input);
		}

		public void update(byte[] input) {
			delegate.update(input);
		}

		public void update(byte[] input, int offset, int len) {
			delegate.update(input, offset, len);
		}		
	}
}
