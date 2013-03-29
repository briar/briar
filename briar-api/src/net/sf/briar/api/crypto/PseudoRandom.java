package net.sf.briar.api.crypto;

/** A deterministic PRNG. */
public interface PseudoRandom {

	byte[] nextBytes(int bytes);
}
