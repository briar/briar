package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
class Curve25519PrivateKey extends Bytes implements PrivateKey {

	Curve25519PrivateKey(byte[] bytes) {
		super(bytes);
	}

	@Override
	public byte[] getEncoded() {
		return getBytes();
	}
}
