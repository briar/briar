package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.PseudoRandom;

import java.security.Provider;
import java.security.SecureRandom;
import java.security.SecureRandomSpi;

class PseudoSecureRandom extends SecureRandom {

	private static final Provider PROVIDER = new PseudoSecureRandomProvider();

	PseudoSecureRandom(byte[] seed) {
		super(new PseudoSecureRandomSpi(seed), PROVIDER);
	}

	private static class PseudoSecureRandomSpi extends SecureRandomSpi {

		private final PseudoRandom pseudoRandom;

		private PseudoSecureRandomSpi(byte[] seed) {
			pseudoRandom = new PseudoRandomImpl(seed);
		}

		@Override
		protected byte[] engineGenerateSeed(int length) {
			return pseudoRandom.nextBytes(length);
		}

		@Override
		protected void engineNextBytes(byte[] b) {
			byte[] random = pseudoRandom.nextBytes(b.length);
			System.arraycopy(random, 0, b, 0, b.length);
		}

		@Override
		protected void engineSetSeed(byte[] seed) {
			// Thank you for your input
		}
	}

	private static class PseudoSecureRandomProvider extends Provider {

		private PseudoSecureRandomProvider() {
			super("PseudoSecureRandom", 1.0, "Only for testing");
		}
	}
}
