package net.sf.briar.crypto;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;

import net.sf.briar.api.crypto.KeyParser;

/**
 * A key parser that uses the encoding defined in "SEC 1: Elliptic Curve
 * Cryptography", section 2.3 (Certicom Corporation, May 2009). Point
 * compression is not used.
 */
class Sec1KeyParser implements KeyParser {

	private final KeyFactory keyFactory;
	private final ECParameterSpec params;
	private final BigInteger modulus;
	private final int bytesPerInt, encodedKeyLength;

	Sec1KeyParser(KeyFactory keyFactory, ECParameterSpec params,
			BigInteger modulus, int keyBits) {
		this.keyFactory = keyFactory;
		this.params = params;
		this.modulus = modulus;
		bytesPerInt = (int) Math.ceil(keyBits / 8.0);
		encodedKeyLength = 1 + 2 * bytesPerInt;
	}

	public PublicKey parsePublicKey(byte[] encodedKey)
			throws InvalidKeySpecException {
		if(encodedKey.length != encodedKeyLength)
			throw new InvalidKeySpecException();
		// The first byte must be 0x04
		if(encodedKey[0] != 4) throw new InvalidKeySpecException();
		// The x co-ordinate must be >= 0 and < q
		byte[] xBytes = new byte[bytesPerInt];
		System.arraycopy(encodedKey, 1, xBytes, 0, bytesPerInt);
		BigInteger x = new BigInteger(1, xBytes); // Positive signum
		if(x.compareTo(modulus) >= 0) throw new InvalidKeySpecException();
		// The y co-ordinate must be >= 0 and < q
		byte[] yBytes = new byte[bytesPerInt];
		System.arraycopy(encodedKey, bytesPerInt + 1, yBytes, 0, bytesPerInt);
		BigInteger y = new BigInteger(1, yBytes); // Positive signum
		if(y.compareTo(modulus) >= 0) throw new InvalidKeySpecException();
		// Verify that y^2 == x^3 + ax + b (mod q)
		BigInteger a = params.getCurve().getA(), b = params.getCurve().getB();
		BigInteger lhs = y.multiply(y).mod(modulus);
		BigInteger rhs = x.multiply(x).add(a).multiply(x).add(b).mod(modulus);
		if(!lhs.equals(rhs)) throw new InvalidKeySpecException();
		// Construct a public key from the point (x, y) and the params
		ECPoint pub = new ECPoint(x, y);
		ECPublicKeySpec keySpec = new ECPublicKeySpec(pub, params);
		return keyFactory.generatePublic(keySpec);
	}
}
