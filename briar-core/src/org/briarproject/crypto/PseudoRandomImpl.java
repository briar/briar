package org.briarproject.crypto;

import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.util.ByteUtils;

class PseudoRandomImpl implements PseudoRandom {

	private final FortunaGenerator generator;

	PseudoRandomImpl(int seed1, int seed2) {
		byte[] seed = new byte[8];
		ByteUtils.writeUint32(seed1, seed, 0);
		ByteUtils.writeUint32(seed2, seed, 4);
		generator = new FortunaGenerator(seed);
	}

	public byte[] nextBytes(int length) {
		byte[] b = new byte[length];
		int offset = 0;
		while(offset < length) offset += generator.nextBytes(b, offset, length);
		return b;
	}
}
