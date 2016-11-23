package org.briarproject.bramble.api.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

/**
 * Uses a platform-specific source to provide a seed for a pseudo-random
 * number generator.
 */
@NotNullByDefault
public interface SeedProvider {

	/**
	 * The length of the seed in bytes.
	 */
	int SEED_BYTES = 32;

	byte[] getSeed();
}
