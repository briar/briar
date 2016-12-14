package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.util.StringUtils;
import org.junit.Test;

import java.security.GeneralSecurityException;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class XSalsa20Poly1305AuthenticatedCipherTest extends BrambleTestCase {

	// Test vectors from the NaCl paper
	// http://cr.yp.to/highspeed/naclcrypto-20090310.pdf
	private static final byte[] TEST_KEY = StringUtils.fromHexString(
			"1b27556473e985d462cd51197a9a46c76009549eac6474f206c4ee0844f68389");
	private static final byte[] TEST_IV = StringUtils.fromHexString(
			"69696ee955b62b73cd62bda875fc73d68219e0036b7a0b37");
	private static final byte[] TEST_PLAINTEXT = StringUtils.fromHexString(
					"be075fc53c81f2d5cf141316" +
					"ebeb0c7b5228c52a4c62cbd4" +
					"4b66849b64244ffce5ecbaaf" +
					"33bd751a1ac728d45e6c6129" +
					"6cdc3c01233561f41db66cce" +
					"314adb310e3be8250c46f06d" +
					"ceea3a7fa1348057e2f6556a" +
					"d6b1318a024a838f21af1fde" +
					"048977eb48f59ffd4924ca1c" +
					"60902e52f0a089bc76897040" +
					"e082f937763848645e0705");
	private static final byte[] TEST_CIPHERTEXT = StringUtils.fromHexString(
					"f3ffc7703f9400e52a7dfb4b" +
					"3d3305d98e993b9f48681273" +
					"c29650ba32fc76ce48332ea7" +
					"164d96a4476fb8c531a1186a" +
					"c0dfc17c98dce87b4da7f011" +
					"ec48c97271d2c20f9b928fe2" +
					"270d6fb863d51738b48eeee3" +
					"14a7cc8ab932164548e526ae" +
					"90224368517acfeabd6bb373" +
					"2bc0e9da99832b61ca01b6de" +
					"56244a9e88d5f9b37973f622" +
					"a43d14a6599b1f654cb45a74" +
					"e355a5");

	@Test
	public void testEncrypt() throws Exception {
		SecretKey k = new SecretKey(TEST_KEY);
		AuthenticatedCipher cipher = new XSalsa20Poly1305AuthenticatedCipher();
		cipher.init(true, k, TEST_IV);
		byte[] output = new byte[TEST_CIPHERTEXT.length];
		assertEquals(TEST_CIPHERTEXT.length, cipher.process(TEST_PLAINTEXT, 0,
						TEST_PLAINTEXT.length, output, 0));
		assertArrayEquals(TEST_CIPHERTEXT, output);
	}

	@Test
	public void testDecrypt() throws Exception {
		SecretKey k = new SecretKey(TEST_KEY);
		AuthenticatedCipher cipher = new XSalsa20Poly1305AuthenticatedCipher();
		cipher.init(false, k, TEST_IV);
		byte[] output = new byte[TEST_PLAINTEXT.length];
		assertEquals(TEST_PLAINTEXT.length, cipher.process(TEST_CIPHERTEXT, 0,
				TEST_CIPHERTEXT.length, output, 0));
		assertArrayEquals(TEST_PLAINTEXT, output);
	}

	@Test(expected = GeneralSecurityException.class)
	public void testDecryptFailsWithShortInput() throws Exception {
		SecretKey k = new SecretKey(TEST_KEY);
		AuthenticatedCipher cipher = new XSalsa20Poly1305AuthenticatedCipher();
		cipher.init(false, k, TEST_IV);
		byte[] input = new byte[cipher.getMacBytes() - 1];
		System.arraycopy(TEST_CIPHERTEXT, 0, input, 0, input.length);
		byte[] output = new byte[TEST_PLAINTEXT.length];
		cipher.process(input, 0, input.length, output, 0);
	}

	@Test(expected = GeneralSecurityException.class)
	public void testDecryptFailsWithAlteredCiphertext() throws Exception {
		SecretKey k = new SecretKey(TEST_KEY);
		AuthenticatedCipher cipher = new XSalsa20Poly1305AuthenticatedCipher();
		cipher.init(false, k, TEST_IV);
		byte[] input = new byte[TEST_CIPHERTEXT.length];
		System.arraycopy(TEST_CIPHERTEXT, 0, input, 0, TEST_CIPHERTEXT.length);
		input[new Random().nextInt(TEST_CIPHERTEXT.length)] ^= 0xFF;
		byte[] output = new byte[TEST_PLAINTEXT.length];
		cipher.process(input, 0, input.length, output, 0);
	}
}
