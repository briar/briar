package net.sf.briar.crypto;

import java.math.BigInteger;

import net.sf.briar.api.crypto.PrivateKey;

import org.spongycastle.crypto.params.ECPrivateKeyParameters;

class Sec1PrivateKey implements PrivateKey {

	private final ECPrivateKeyParameters key;
	private final int privateKeyBytes;

	Sec1PrivateKey(ECPrivateKeyParameters key, int keyBits) {
		this.key = key;
		privateKeyBytes = (int) Math.ceil(keyBits / 8.0);
	}

	public byte[] getEncoded() {
		byte[] encodedKey = new byte[privateKeyBytes];
		BigInteger d = key.getD();
		// Copy up to privateKeyBytes bytes into exactly privateKeyBytes bytes
		byte[] dBytes = d.toByteArray();
		for(int i = 0; i < dBytes.length && i < privateKeyBytes; i++)
			encodedKey[privateKeyBytes - 1 - i] = dBytes[dBytes.length - 1 - i];
		return encodedKey;
	}

	ECPrivateKeyParameters getKey() {
		return key;
	}
}
