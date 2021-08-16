package org.briarproject.bramble.crypto;

import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.util.StringUtils;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertArrayEquals;

public class Blake2bDigestTest extends BrambleTestCase {

	// Vectors from BLAKE2 web site: https://blake2.net/Blake2b-test.txt
	private static final String[][] KEYED_TEST_VECTORS = {
			// input/message, key, hash
			{
					"",
					"000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f",
					"10ebb67700b1868efb4417987acf4690ae9d972fb7a590c2f02871799aaa4786b5e996e8f0f4eb981fc214b005f42d2ff4233499391653df7aefcbc13fc51568",
			},
			{
					"00",
					"000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f",
					"961f6dd1e4dd30f63901690c512e78e4b45e4742ed197c3c5e45c549fd25f2e4187b0bc9fe30492b16b0d0bc4ef9b0f34c7003fac09a5ef1532e69430234cebd",
			},
			{
					"0001",
					"000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f",
					"da2cfbe2d8409a0f38026113884f84b50156371ae304c4430173d08a99d9fb1b983164a3770706d537f49e0c916d9f32b95cc37a95b99d857436f0232c88a965",
			},
			{
					"000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d",
					"000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f",
					"f1aa2b044f8f0c638a3f362e677b5d891d6fd2ab0765f6ee1e4987de057ead357883d9b405b9d609eea1b869d97fb16d9b51017c553f3b93c0a1e0f1296fedcd",
			},
			{
					"000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9fa0a1a2a3",
					"000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f",
					"c230f0802679cb33822ef8b3b21bf7a9a28942092901d7dac3760300831026cf354c9232df3e084d9903130c601f63c1f4a4a4b8106e468cd443bbe5a734f45f",
			},
			{
					"000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9fa0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfe",
					"000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f",
					"142709d62e28fcccd0af97fad0f8465b971e82201dc51070faa0372aa43e92484be1c1e73ba10906d5d1853db6a4106e0a7bf9800d373d6dee2d46d62ef2a461",
			},
	};

	@Test
	public void testDigestWithKeyedTestVectors() {
		for (String[] keyedTestVector : KEYED_TEST_VECTORS) {
			byte[] input = StringUtils.fromHexString(keyedTestVector[0]);
			byte[] key = StringUtils.fromHexString(keyedTestVector[1]);
			byte[] expected = StringUtils.fromHexString(keyedTestVector[2]);

			Blake2bDigest digest = new Blake2bDigest(key);
			digest.update(input, 0, input.length);
			byte[] hash = new byte[64];
			digest.doFinal(hash, 0);

			assertArrayEquals(expected, hash);
		}
	}

	@Test
	public void testDigestWithKeyedTestVectorsAndRandomUpdate() {
		Random random = new Random();
		for (int i = 0; i < 100; i++) {
			for (String[] keyedTestVector : KEYED_TEST_VECTORS) {
				byte[] input = StringUtils.fromHexString(keyedTestVector[0]);
				if (input.length == 0) continue;
				byte[] key = StringUtils.fromHexString(keyedTestVector[1]);
				byte[] expected = StringUtils.fromHexString(keyedTestVector[2]);

				Blake2bDigest digest = new Blake2bDigest(key);

				int pos = random.nextInt(input.length);
				if (pos > 0)
					digest.update(input, 0, pos);
				digest.update(input[pos]);
				if (pos < (input.length - 1))
					digest.update(input, pos + 1, input.length - (pos + 1));

				byte[] hash = new byte[64];
				digest.doFinal(hash, 0);

				assertArrayEquals(expected, hash);
			}
		}
	}

	@Test
	public void testReset() {
		// Generate a non-zero key
		byte[] key = new byte[32];
		for (byte i = 0; i < key.length; i++) key[i] = i;
		// Generate some non-zero input longer than the key
		byte[] input = new byte[key.length + 1];
		for (byte i = 0; i < input.length; i++) input[i] = i;
		// Hash the input
		Blake2bDigest digest = new Blake2bDigest(key);
		digest.update(input, 0, input.length);
		byte[] hash = new byte[digest.getDigestSize()];
		digest.doFinal(hash, 0);
		// Create a second instance, hash the input without calling doFinal()
		Blake2bDigest digest1 = new Blake2bDigest(key);
		digest1.update(input, 0, input.length);
		// Reset the second instance and hash the input again
		digest1.reset();
		digest1.update(input, 0, input.length);
		byte[] hash1 = new byte[digest.getDigestSize()];
		digest1.doFinal(hash1, 0);
		// The hashes should be identical
		assertArrayEquals(hash, hash1);
	}

	// Self-test routine from https://tools.ietf.org/html/rfc7693#appendix-E
	private static final String SELF_TEST_RESULT =
			"C23A7800D98123BD10F506C61E29DA5603D763B8BBAD2E737F5E765A7BCCD475";
	private static final int[] SELF_TEST_DIGEST_LEN = {20, 32, 48, 64};
	private static final int[] SELF_TEST_INPUT_LEN =
			{0, 3, 128, 129, 255, 1024};

	private static byte[] selfTestSequence(int len, int seed) {
		int a = 0xDEAD4BAD * seed;
		int b = 1;
		int t;
		byte[] out = new byte[len];

		for (int i = 0; i < len; i++) {
			t = a + b;
			a = b;
			b = t;
			out[i] = (byte) ((t >> 24) & 0xFF);
		}

		return out;
	}

	@Test
	public void runSelfTest() {
		Blake2bDigest testDigest = new Blake2bDigest(256);
		byte[] md = new byte[64];

		for (int i = 0; i < 4; i++) {
			int outlen = SELF_TEST_DIGEST_LEN[i];
			for (int j = 0; j < 6; j++) {
				int inlen = SELF_TEST_INPUT_LEN[j];

				// unkeyed hash
				byte[] in = selfTestSequence(inlen, inlen);
				Blake2bDigest unkeyedDigest = new Blake2bDigest(outlen * 8);
				unkeyedDigest.update(in, 0, inlen);
				unkeyedDigest.doFinal(md, 0);
				// hash the hash
				testDigest.update(md, 0, outlen);

				// keyed hash
				byte[] key = selfTestSequence(outlen, outlen);
				Blake2bDigest keyedDigest = new Blake2bDigest(key, outlen, null,
						null);
				keyedDigest.update(in, 0, inlen);
				keyedDigest.doFinal(md, 0);
				// hash the hash
				testDigest.update(md, 0, outlen);
			}
		}

		byte[] hash = new byte[32];
		testDigest.doFinal(hash, 0);
		assertArrayEquals(StringUtils.fromHexString(SELF_TEST_RESULT), hash);
	}
}
