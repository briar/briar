package net.sf.briar.protocol;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Arrays;
import java.util.Random;

import junit.framework.TestCase;
import net.sf.briar.api.serial.FormatException;

import org.junit.Test;

public class ConsumersTest extends TestCase {

	private static final String SIGNATURE_ALGO = "SHA256withRSA";
	private static final String KEY_PAIR_ALGO = "RSA";
	private static final String DIGEST_ALGO = "SHA-256";

	@Test
	public void testSigningConsumer() throws Exception {
		Signature s = Signature.getInstance(SIGNATURE_ALGO);
		KeyPair k = KeyPairGenerator.getInstance(KEY_PAIR_ALGO).genKeyPair();
		byte[] data = new byte[1234];
		// Generate some random data and sign it
		new Random().nextBytes(data);
		s.initSign(k.getPrivate());
		s.update(data);
		byte[] sig = s.sign();
		// Check that feeding a SigningConsumer generates the same signature
		s.initSign(k.getPrivate());
		SigningConsumer sc = new SigningConsumer(s);
		sc.write(data[0]);
		sc.write(data, 1, data.length - 2);
		sc.write(data[data.length - 1]);
		byte[] sig1 = s.sign();
		assertTrue(Arrays.equals(sig, sig1));
	}

	@Test
	public void testDigestingConsumer() throws Exception {
		MessageDigest m = MessageDigest.getInstance(DIGEST_ALGO);
		byte[] data = new byte[1234];
		// Generate some random data and digest it
		new Random().nextBytes(data);
		m.reset();
		m.update(data);
		byte[] dig = m.digest();
		// Check that feeding a DigestingConsumer generates the same digest
		m.reset();
		DigestingConsumer dc = new DigestingConsumer(m);
		dc.write(data[0]);
		dc.write(data, 1, data.length - 2);
		dc.write(data[data.length - 1]);
		byte[] dig1 = m.digest();
		assertTrue(Arrays.equals(dig, dig1));
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
			assertTrue(false);
		} catch(FormatException expected) {}
	}
}
