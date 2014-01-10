package org.briarproject.crypto;

import org.briarproject.api.crypto.PrivateKey;

import org.spongycastle.crypto.params.ECPrivateKeyParameters;

class Sec1PrivateKey implements PrivateKey {

	private final ECPrivateKeyParameters key;
	private final int bytesPerInt;

	Sec1PrivateKey(ECPrivateKeyParameters key, int keyBits) {
		this.key = key;
		bytesPerInt = (keyBits + 7) / 8;
	}

	public byte[] getEncoded() {
		byte[] encodedKey = new byte[bytesPerInt];
		byte[] d = key.getD().toByteArray();
		Sec1Utils.convertToFixedLength(d, encodedKey, 0, bytesPerInt);
		return encodedKey;
	}

	ECPrivateKeyParameters getKey() {
		return key;
	}
}
