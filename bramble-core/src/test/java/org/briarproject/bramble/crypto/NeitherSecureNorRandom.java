package org.briarproject.bramble.crypto;

import java.security.Provider;
import java.security.SecureRandom;
import java.security.SecureRandomSpi;

import static java.util.Arrays.fill;

/**
 * A fake SecureRandom implementation for testing, which returns all zeroes.
 */
public class NeitherSecureNorRandom extends SecureRandom {

	private static final Provider PROVIDER =
			new NeitherSecureNorRandomProvider();

	public NeitherSecureNorRandom() {
		super(new NeitherSecureNorRandomSpi(), PROVIDER);
	}

	private static class NeitherSecureNorRandomSpi extends SecureRandomSpi {

		@Override
		protected byte[] engineGenerateSeed(int length) {
			return new byte[length];
		}

		@Override
		protected void engineNextBytes(byte[] b) {
			fill(b, (byte) 0);
		}

		@Override
		protected void engineSetSeed(byte[] seed) {
			// Thank you for your input
		}
	}

	private static class NeitherSecureNorRandomProvider extends Provider {

		private NeitherSecureNorRandomProvider() {
			super("NeitherSecureNorRandom", 1.0, "Only for testing");
		}
	}
}
