package org.briarproject.bramble.rendezvous;

import org.bouncycastle.crypto.engines.Salsa20Engine;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.rendezvous.KeyMaterialSource;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
class KeyMaterialSourceImpl implements KeyMaterialSource {

	@GuardedBy("this")
	private final Salsa20Engine cipher = new Salsa20Engine();

	KeyMaterialSourceImpl(SecretKey sourceKey) {
		// Initialise the stream cipher with an all-zero nonce
		KeyParameter k = new KeyParameter(sourceKey.getBytes());
		cipher.init(true, new ParametersWithIV(k, new byte[8]));
	}

	@Override
	public synchronized byte[] getKeyMaterial(int length) {
		byte[] in = new byte[length];
		byte[] out = new byte[length];
		cipher.processBytes(in, 0, length, out, 0);
		return out;
	}
}
