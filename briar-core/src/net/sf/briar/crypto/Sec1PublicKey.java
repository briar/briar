package net.sf.briar.crypto;

import net.sf.briar.api.crypto.PublicKey;

import org.spongycastle.crypto.params.ECPublicKeyParameters;

/**
 * An elliptic curve public key that uses the encoding defined in "SEC 1:
 * Elliptic Curve Cryptography", section 2.3 (Certicom Corporation, May 2009).
 * Point compression is not used.
 */
class Sec1PublicKey implements PublicKey {

	private final ECPublicKeyParameters key;
	private final int bytesPerInt, publicKeyBytes;

	Sec1PublicKey(ECPublicKeyParameters key, int keyBits) {
		this.key = key;
		bytesPerInt = (keyBits + 7) / 8;
		publicKeyBytes = 1 + 2 * bytesPerInt;
	}

	public byte[] getEncoded() {
		byte[] encodedKey = new byte[publicKeyBytes];
		encodedKey[0] = 4;
		byte[] x = key.getQ().getX().toBigInteger().toByteArray();
		Sec1Utils.convertToFixedLength(x, encodedKey, bytesPerInt, 1);
		byte[] y = key.getQ().getY().toBigInteger().toByteArray();
		Sec1Utils.convertToFixedLength(y, encodedKey, bytesPerInt,
				1 + bytesPerInt);
		return encodedKey;
	}

	ECPublicKeyParameters getKey() {
		return key;
	}
}
