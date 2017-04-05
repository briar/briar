package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.PseudoRandom;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.engines.Salsa20Engine;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.crypto.params.ParametersWithIV;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class PseudoRandomImpl implements PseudoRandom {

	private final Salsa20Engine cipher = new Salsa20Engine();

	PseudoRandomImpl(byte[] seed) {
		// Hash the seed to produce a 32-byte key
		byte[] key = new byte[32];
		Digest digest = new Blake2sDigest();
		digest.update(seed, 0, seed.length);
		digest.doFinal(key, 0);
		// Initialise the stream cipher with an all-zero nonce
		byte[] nonce = new byte[8];
		cipher.init(true, new ParametersWithIV(new KeyParameter(key), nonce));
	}

	@Override
	public byte[] nextBytes(int length) {
		byte[] in = new byte[length], out = new byte[length];
		cipher.processBytes(in, 0, length, out, 0);
		return out;
	}
}
