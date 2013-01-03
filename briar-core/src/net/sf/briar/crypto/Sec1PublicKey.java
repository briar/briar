package net.sf.briar.crypto;

import java.math.BigInteger;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

/**
 * An elliptic curve public key that uses the encoding defined in "SEC 1:
 * Elliptic Curve Cryptography", section 2.3 (Certicom Corporation, May 2009).
 * Point compression is not used.
 */
class Sec1PublicKey implements ECPublicKey {

	private static final long serialVersionUID = -2722797033851423987L;

	private final ECPublicKey key;
	private final int bytesPerInt, encodedKeyLength;

	Sec1PublicKey(ECPublicKey key, int keyBits) {
		this.key = key;
		bytesPerInt = (int) Math.ceil(keyBits / 8.0);
		encodedKeyLength = 1 + 2 * bytesPerInt;
	}

	public String getAlgorithm() {
		return key.getAlgorithm();
	}

	public byte[] getEncoded() {
		byte[] encodedKey = new byte[encodedKeyLength];
		encodedKey[0] = 4;
		BigInteger x = key.getW().getAffineX(), y = key.getW().getAffineY();
		// Copy up to bytesPerInt bytes into exactly bytesPerInt bytes
		byte[] xBytes = x.toByteArray();
		for(int i = 0; i < xBytes.length && i < bytesPerInt; i++)
			encodedKey[bytesPerInt - i] = xBytes[xBytes.length - 1 - i];
		byte[] yBytes = y.toByteArray();
		for(int i = 0; i < yBytes.length && i < bytesPerInt; i++)
			encodedKey[2 * bytesPerInt - i] = yBytes[yBytes.length - 1 - i];
		return encodedKey;
	}

	public String getFormat() {
		return "SEC1";
	}

	public ECParameterSpec getParams() {
		return key.getParams();
	}

	public ECPoint getW() {
		return key.getW();
	}
}
