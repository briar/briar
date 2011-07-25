package net.sf.briar.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Arrays;
import java.util.Random;

import junit.framework.TestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class SigningDigestingOutputStreamTest extends TestCase {

	private CryptoComponent crypto = null;

	@Before
	public void setUp() throws Exception {
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
	}

	@Test
	public void testStopAndStart() throws Exception {
		Signature signature = crypto.getSignature();
		KeyPair keyPair = crypto.generateKeyPair();
		MessageDigest messageDigest = crypto.getMessageDigest();
		byte[] input = new byte[1024];
		new Random().nextBytes(input);
		ByteArrayOutputStream out = new ByteArrayOutputStream(input.length);
		SigningDigestingOutputStream s =
			new SigningDigestingOutputStream(out, signature, messageDigest);
		signature.initSign(keyPair.getPrivate());
		messageDigest.reset();
		// Sign the first 256 bytes, digest all but the last 256 bytes
		s.setDigesting(true);
		s.setSigning(true);
		s.write(input, 0, 256);
		s.setSigning(false);
		s.write(input, 256, 512);
		s.setDigesting(false);
		s.write(input, 768, 256);
		s.close();
		// Get the signature and the digest
		byte[] sig = signature.sign();
		byte[] digest = messageDigest.digest();
		// Check that the output matches the input
		assertTrue(Arrays.equals(input, out.toByteArray()));
		// Check that the signature matches a signature over the first 256 bytes
		signature.initSign(keyPair.getPrivate());
		signature.update(input, 0, 256);
		byte[] directSig = signature.sign();
		assertTrue(Arrays.equals(directSig, sig));
		// Check that the digest matches a digest over all but the last 256
		// bytes
		messageDigest.reset();
		messageDigest.update(input, 0, 768);
		byte[] directDigest = messageDigest.digest();
		assertTrue(Arrays.equals(directDigest, digest));
	}

	@Test
	public void testSignatureExceptionThrowsIOException() throws Exception {
		Signature signature = crypto.getSignature();
		MessageDigest messageDigest = crypto.getMessageDigest();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		SigningDigestingOutputStream s =
			new SigningDigestingOutputStream(out, signature, messageDigest);
		s.setSigning(true); // Signature hasn't been initialised yet
		try {
			s.write((byte) 0);
			assertTrue(false);
		} catch(IOException expected) {};
	}
}
