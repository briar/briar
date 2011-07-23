package net.sf.briar.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Arrays;
import java.util.Random;

import junit.framework.TestCase;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Before;
import org.junit.Test;

public class SigningDigestingOutputStreamTest extends TestCase {

	private KeyPair keyPair = null;
	private Signature sig = null;
	private MessageDigest dig = null;

	@Before
	public void setUp() throws Exception {
		KeyPairGenerator gen =
			KeyPairGenerator.getInstance(CryptoModule.KEY_PAIR_ALGO);
		keyPair = gen.generateKeyPair();
		sig = Signature.getInstance(CryptoModule.SIGNATURE_ALGO);
		dig = MessageDigest.getInstance(CryptoModule.DIGEST_ALGO);
	}

	@Test
	public void testStopAndStart() throws Exception {
		byte[] input = new byte[1024];
		new Random().nextBytes(input);
		ByteArrayOutputStream out = new ByteArrayOutputStream(input.length);
		SigningDigestingOutputStream s =
			new SigningDigestingOutputStream(out, sig, dig);
		sig.initSign(keyPair.getPrivate());
		dig.reset();
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
		byte[] signature = sig.sign();
		byte[] digest = dig.digest();
		// Check that the output matches the input
		assertTrue(Arrays.equals(input, out.toByteArray()));
		// Check that the signature matches a signature over the first 256 bytes
		sig.initSign(keyPair.getPrivate());
		sig.update(input, 0, 256);
		byte[] directSignature = sig.sign();
		assertTrue(Arrays.equals(directSignature, signature));
		// Check that the digest matches a digest over all but the last 256
		// bytes
		dig.reset();
		dig.update(input, 0, 768);
		byte[] directDigest = dig.digest();
		assertTrue(Arrays.equals(directDigest, digest));
	}

	@Test
	public void testSignatureExceptionThrowsIOException() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		SigningDigestingOutputStream s =
			new SigningDigestingOutputStream(out, sig, dig);
		s.setSigning(true); // Signature hasn't been initialised yet
		try {
			s.write((byte) 0);
			assertTrue(false);
		} catch(IOException expected) {};
	}
}
