package net.sf.briar.protocol;

import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Arrays;
import java.util.Random;

import junit.framework.TestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.serial.FormatException;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConsumersTest extends TestCase {

	private CryptoComponent crypto = null;

	@Before
	public void setUp() {
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
	}
		
	@Test
	public void testSigningConsumer() throws Exception {
		Signature signature = crypto.getSignature();
		KeyPair keyPair = crypto.generateKeyPair();
		byte[] data = new byte[1234];
		// Generate some random data and sign it
		new Random().nextBytes(data);
		signature.initSign(keyPair.getPrivate());
		signature.update(data);
		byte[] sig = signature.sign();
		// Check that feeding a SigningConsumer generates the same signature
		signature.initSign(keyPair.getPrivate());
		SigningConsumer sc = new SigningConsumer(signature);
		sc.write(data[0]);
		sc.write(data, 1, data.length - 2);
		sc.write(data[data.length - 1]);
		byte[] sig1 = signature.sign();
		assertTrue(Arrays.equals(sig, sig1));
	}

	@Test
	public void testDigestingConsumer() throws Exception {
		MessageDigest messageDigest = crypto.getMessageDigest();
		byte[] data = new byte[1234];
		// Generate some random data and digest it
		new Random().nextBytes(data);
		messageDigest.reset();
		messageDigest.update(data);
		byte[] dig = messageDigest.digest();
		// Check that feeding a DigestingConsumer generates the same digest
		messageDigest.reset();
		DigestingConsumer dc = new DigestingConsumer(messageDigest);
		dc.write(data[0]);
		dc.write(data, 1, data.length - 2);
		dc.write(data[data.length - 1]);
		byte[] dig1 = messageDigest.digest();
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

	@Test
	public void testCopyingConsumer() throws Exception {
		byte[] data = new byte[1234];
		new Random().nextBytes(data);
		// Check that a CopyingConsumer creates a faithful copy
		CopyingConsumer cc = new CopyingConsumer();
		cc.write(data[0]);
		cc.write(data, 1, data.length - 2);
		cc.write(data[data.length - 1]);
		assertTrue(Arrays.equals(data, cc.getCopy()));
	}
}
