package net.sf.briar.crypto;

import java.math.BigInteger;

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
		bytesPerInt = (int) Math.ceil(keyBits / 8.0);
		publicKeyBytes = 1 + 2 * bytesPerInt;
	}

	public byte[] getEncoded() {
		byte[] encodedKey = new byte[publicKeyBytes];
		encodedKey[0] = 4;
		BigInteger x = key.getQ().getX().toBigInteger();
		BigInteger y = key.getQ().getY().toBigInteger();
		// Copy up to bytesPerInt bytes into exactly bytesPerInt bytes
		byte[] xBytes = x.toByteArray();
		for(int i = 0; i < xBytes.length && i < bytesPerInt; i++)
			encodedKey[bytesPerInt - i] = xBytes[xBytes.length - 1 - i];
		byte[] yBytes = y.toByteArray();
		for(int i = 0; i < yBytes.length && i < bytesPerInt; i++)
			encodedKey[2 * bytesPerInt - i] = yBytes[yBytes.length - 1 - i];
		return encodedKey;
	}

	ECPublicKeyParameters getKey() {
		return key;
	}
}
