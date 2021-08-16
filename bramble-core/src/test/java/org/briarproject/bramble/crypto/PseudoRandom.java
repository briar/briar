package org.briarproject.bramble.crypto;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.bouncycastle.crypto.engines.Salsa20Engine;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
class PseudoRandom {

	private final Salsa20Engine cipher = new Salsa20Engine();

	PseudoRandom(byte[] seed) {
		// Hash the seed to produce a 32-byte key
		byte[] key = new byte[32];
		Digest digest = new Blake2bDigest(256);
		digest.update(seed, 0, seed.length);
		digest.doFinal(key, 0);
		// Initialise the stream cipher with an all-zero nonce
		byte[] nonce = new byte[8];
		cipher.init(true, new ParametersWithIV(new KeyParameter(key), nonce));
	}

	byte[] nextBytes(int length) {
		byte[] in = new byte[length], out = new byte[length];
		cipher.processBytes(in, 0, length, out, 0);
		return out;
	}
}
