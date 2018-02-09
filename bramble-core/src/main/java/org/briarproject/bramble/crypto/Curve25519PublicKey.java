package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
class Curve25519PublicKey extends Bytes implements PublicKey {

	Curve25519PublicKey(byte[] bytes) {
		super(bytes);
	}

	@Override
	public byte[] getEncoded() {
		return getBytes();
	}
}
