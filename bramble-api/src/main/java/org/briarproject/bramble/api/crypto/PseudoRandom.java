package org.briarproject.bramble.api.crypto;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

/**
 * A deterministic pseudo-random number generator.
 */
@NotNullByDefault
public interface PseudoRandom {

	byte[] nextBytes(int bytes);
}
