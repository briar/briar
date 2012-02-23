package net.sf.briar.api.crypto;

public interface PseudoRandom {

	byte[] nextBytes(int bytes);
}
