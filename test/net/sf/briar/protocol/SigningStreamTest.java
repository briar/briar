package net.sf.briar.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.DigestOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Arrays;
import java.util.Random;

import junit.framework.TestCase;

import org.junit.Test;

public class SigningStreamTest extends TestCase {

	private static final String SIGNATURE_ALGO = "SHA256withRSA";
	private static final String KEY_PAIR_ALGO = "RSA";
	private static final String DIGEST_ALGO = "SHA-256";

	private final KeyPair keyPair;
	private final Signature sig;
	private final MessageDigest dig;
	private final Random random;

	public SigningStreamTest() throws Exception {
		super();
		keyPair = KeyPairGenerator.getInstance(KEY_PAIR_ALGO).generateKeyPair();
		sig = Signature.getInstance(SIGNATURE_ALGO);
		dig = MessageDigest.getInstance(DIGEST_ALGO);
		random = new Random();
	}

	@Test
	public void testOutputStreamOutputMatchesInput() throws Exception {
		byte[] input = new byte[1000];
		random.nextBytes(input);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		SigningOutputStream signOut = new SigningOutputStream(out, sig);
		sig.initSign(keyPair.getPrivate());

		signOut.setSigning(true);
		signOut.write(input, 0, 500);
		signOut.setSigning(false);
		signOut.write(input, 500, 250);
		signOut.setSigning(true);
		signOut.write(input, 750, 250);

		byte[] output = out.toByteArray();
		assertTrue(Arrays.equals(input, output));
	}

	@Test
	public void testInputStreamOutputMatchesInput() throws Exception {
		byte[] input = new byte[1000];
		random.nextBytes(input);

		ByteArrayInputStream in = new ByteArrayInputStream(input);
		SigningDigestingInputStream signIn =
			new SigningDigestingInputStream(in, sig, dig);
		sig.initVerify(keyPair.getPublic());

		byte[] output = new byte[1000];
		signIn.setSigning(true);
		assertEquals(500, signIn.read(output, 0, 500));
		signIn.setSigning(false);
		assertEquals(250, signIn.read(output, 500, 250));
		signIn.setSigning(true);
		assertEquals(250, signIn.read(output, 750, 250));

		assertTrue(Arrays.equals(input, output));
	}

	@Test
	public void testVerificationLagsByOneByte() throws Exception {
		byte[] input = new byte[1000];
		random.nextBytes(input);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		SigningOutputStream signOut = new SigningOutputStream(out, sig);
		sig.initSign(keyPair.getPrivate());

		// Sign bytes 0-499, skip bytes 500-749, sign bytes 750-999
		signOut.setSigning(true);
		signOut.write(input, 0, 500);
		signOut.setSigning(false);
		signOut.write(input, 500, 250);
		signOut.setSigning(true);
		signOut.write(input, 750, 250);

		byte[] signature = sig.sign();

		ByteArrayInputStream in = new ByteArrayInputStream(input);
		SigningDigestingInputStream signIn =
			new SigningDigestingInputStream(in, sig, dig);
		sig.initVerify(keyPair.getPublic());

		byte[] output = new byte[1000];
		// Consume a lookahead byte
		assertEquals(1, signIn.read(output, 0, 1));
		// All the offsets are increased by 1 because of the lookahead byte
		signIn.setSigning(true);
		assertEquals(500, signIn.read(output, 1, 500));
		signIn.setSigning(false);
		assertEquals(250, signIn.read(output, 501, 250));
		signIn.setSigning(true);
		assertEquals(249, signIn.read(output, 751, 249));
		// Have to reach EOF for the lookahead byte to be processed
		assertEquals(-1, signIn.read());

		assertTrue(Arrays.equals(input, output));
		assertTrue(sig.verify(signature));
	}

	@Test
	public void testDigestionLagsByOneByte() throws Exception {
		byte[] input = new byte[1000];
		random.nextBytes(input);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DigestOutputStream digOut = new DigestOutputStream(out, dig);
		dig.reset();

		// Digest bytes 0-499, skip bytes 500-749, digest bytes 750-999
		digOut.on(true);
		digOut.write(input, 0, 500);
		digOut.on(false);
		digOut.write(input, 500, 250);
		digOut.on(true);
		digOut.write(input, 750, 250);

		byte[] hash = dig.digest();

		ByteArrayInputStream in = new ByteArrayInputStream(input);
		SigningDigestingInputStream signIn =
			new SigningDigestingInputStream(in, sig, dig);
		dig.reset();

		byte[] output = new byte[1000];
		// Consume a lookahead byte
		assertEquals(1, signIn.read(output, 0, 1));
		// All the offsets are increased by 1 because of the lookahead byte
		signIn.setDigesting(true);
		assertEquals(500, signIn.read(output, 1, 500));
		signIn.setDigesting(false);
		assertEquals(250, signIn.read(output, 501, 250));
		signIn.setDigesting(true);
		assertEquals(249, signIn.read(output, 751, 249));
		// Have to reach EOF for the lookahead byte to be processed
		assertEquals(-1, signIn.read());

		assertTrue(Arrays.equals(input, output));
		assertTrue(Arrays.equals(hash, dig.digest()));
	}
}