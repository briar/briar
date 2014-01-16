package org.briarproject.crypto;

import org.briarproject.api.crypto.PublicKey;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.math.ec.ECPoint;

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
		ECPoint pub = key.getQ().normalize();
		byte[] x = pub.getAffineXCoord().toBigInteger().toByteArray();
		Sec1Utils.convertToFixedLength(x, encodedKey, 1, bytesPerInt);
		byte[] y = pub.getAffineYCoord().toBigInteger().toByteArray();
		Sec1Utils.convertToFixedLength(y, encodedKey, 1 + bytesPerInt,
				bytesPerInt);
		return encodedKey;
	}

	ECPublicKeyParameters getKey() {
		return key;
	}
}
