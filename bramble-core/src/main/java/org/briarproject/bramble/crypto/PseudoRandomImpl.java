package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.PseudoRandom;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.ByteUtils;

import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.bramble.util.ByteUtils.INT_32_BYTES;

@NotThreadSafe
@NotNullByDefault
class PseudoRandomImpl implements PseudoRandom {

	private final FortunaGenerator generator;

	PseudoRandomImpl(int seed1, int seed2) {
		byte[] seed = new byte[INT_32_BYTES * 2];
		ByteUtils.writeUint32(seed1, seed, 0);
		ByteUtils.writeUint32(seed2, seed, INT_32_BYTES);
		generator = new FortunaGenerator(seed);
	}

	@Override
	public byte[] nextBytes(int length) {
		byte[] b = new byte[length];
		int offset = 0;
		while (offset < length) offset += generator.nextBytes(b, offset, length);
		return b;
	}
}
