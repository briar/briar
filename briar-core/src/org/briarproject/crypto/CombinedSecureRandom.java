package org.briarproject.crypto;

import java.security.Provider;
import java.security.SecureRandom;
import java.security.SecureRandomSpi;

/**
 * A {@link java.security.SecureRandom SecureRandom} implementation that
 * combines the outputs of two or more other implementations using XOR.
 */
class CombinedSecureRandom extends SecureRandom {

	private static final long serialVersionUID = -605269609862397104L;
	private static final Provider PROVIDER = new CombinedProvider();

	CombinedSecureRandom(SecureRandom... randoms) {
		super(new CombinedSecureRandomSpi(randoms), PROVIDER);
	}

	private static class CombinedSecureRandomSpi extends SecureRandomSpi {

		private static final long serialVersionUID = 483801767899979081L;

		private final SecureRandom[] randoms;

		private CombinedSecureRandomSpi(SecureRandom... randoms) {
			if (randoms.length < 2) throw new IllegalArgumentException();
			this.randoms = randoms;
		}

		@Override
		protected byte[] engineGenerateSeed(int numBytes) {
			byte[] combined = new byte[numBytes];
			for (SecureRandom random : randoms) {
				byte[] b = random.generateSeed(numBytes);
				int length = Math.min(numBytes, b.length);
				for (int i = 0; i < length; i++)
					combined[i] = (byte) (combined[i] ^ b[i]);
			}
			return combined;
		}

		@Override
		protected void engineNextBytes(byte[] b) {
			byte[] temp = new byte[b.length];
			for (SecureRandom random : randoms) {
				random.nextBytes(temp);
				for (int i = 0; i < b.length; i++)
					b[i] = (byte) (b[i] ^ temp[i]);
			}
		}

		@Override
		protected void engineSetSeed(byte[] seed) {
			for (SecureRandom random : randoms) random.setSeed(seed);
		}
	}

	private static class CombinedProvider extends Provider {

		private static final long serialVersionUID = -4678501890053703844L;

		private CombinedProvider() {
			super("Combined", 1.0, "");
		}
	}
}
