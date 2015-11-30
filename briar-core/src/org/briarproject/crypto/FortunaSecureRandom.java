package org.briarproject.crypto;

import java.security.Provider;
import java.security.SecureRandom;
import java.security.SecureRandomSpi;
import java.util.Arrays;

import org.briarproject.util.StringUtils;

/**
 * A {@link java.security.SecureRandom SecureRandom} implementation based on a
 * {@link FortunaGenerator}.
 */
class FortunaSecureRandom extends SecureRandom {

	// Package access for testing
	static final byte[] SELF_TEST_VECTOR_1 =
			StringUtils.fromHexString("4BD6EA599D47E3EE9DD911833C29CA22");
	static final byte[] SELF_TEST_VECTOR_2 =
			StringUtils.fromHexString("10984D576E6850E505CA9F42A9BFD88A");
	static final byte[] SELF_TEST_VECTOR_3 =
			StringUtils.fromHexString("1E12DA166BD86DCECDE50A8296018DE2");

	private static final long serialVersionUID = -417332227850184134L;
	private static final Provider PROVIDER = new FortunaProvider();

	FortunaSecureRandom(byte[] seed) {
		super(new FortunaSecureRandomSpi(seed), PROVIDER);
	}

	/**
	 * Tests that the {@link #nextBytes(byte[])} and {@link #setSeed(byte[])}
	 * methods are passed through to the generator in the expected way.
	 */
	static boolean selfTest() {
		byte[] seed = new byte[32];
		SecureRandom r = new FortunaSecureRandom(seed);
		byte[] output = new byte[16];
		r.nextBytes(output);
		if (!Arrays.equals(SELF_TEST_VECTOR_1, output)) return false;
		r.nextBytes(output);
		if (!Arrays.equals(SELF_TEST_VECTOR_2, output)) return false;
		r.setSeed(seed);
		r.nextBytes(output);
		if (!Arrays.equals(SELF_TEST_VECTOR_3, output)) return false;
		return true;
	}

	private static class FortunaSecureRandomSpi extends SecureRandomSpi {

		private static final long serialVersionUID = -1677799887497202351L;

		private final FortunaGenerator generator;

		private FortunaSecureRandomSpi(byte[] seed) {
			generator = new FortunaGenerator(seed);
		}

		@Override
		protected byte[] engineGenerateSeed(int numBytes) {
			byte[] b = new byte[numBytes];
			engineNextBytes(b);
			return b;
		}

		@Override
		protected void engineNextBytes(byte[] b) {
			int offset = 0;
			while (offset < b.length)
				offset += generator.nextBytes(b, offset, b.length - offset);
		}

		@Override
		protected void engineSetSeed(byte[] seed) {
			generator.reseed(seed);
		}
	}

	private static class FortunaProvider extends Provider {

		private static final long serialVersionUID = -833121797778381769L;

		private FortunaProvider() {
			super("Fortuna", 1.0, "");
		}
	}
}
