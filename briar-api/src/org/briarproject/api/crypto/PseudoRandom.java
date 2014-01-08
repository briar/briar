package org.briarproject.api.crypto;

/** A deterministic PRNG. */
public interface PseudoRandom {

	byte[] nextBytes(int bytes);
}
