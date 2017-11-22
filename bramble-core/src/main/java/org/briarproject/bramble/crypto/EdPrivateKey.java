package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
class EdPrivateKey extends Bytes implements PrivateKey {

	EdPrivateKey(byte[] bytes) {
		super(bytes);
	}

	@Override
	public byte[] getEncoded() {
		return getBytes();
	}
}
