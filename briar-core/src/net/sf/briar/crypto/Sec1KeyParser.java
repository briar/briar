package net.sf.briar.crypto;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
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
	private final int keyBits, bytesPerInt, publicKeyBytes, privateKeyBytes;

	Sec1KeyParser(KeyFactory keyFactory, ECParameterSpec params,
			BigInteger modulus, int keyBits) {
		this.keyFactory = keyFactory;
		this.params = params;
		this.modulus = modulus;
		this.keyBits = keyBits;
		bytesPerInt = (int) Math.ceil(keyBits / 8.0);
		publicKeyBytes = 1 + 2 * bytesPerInt;
		privateKeyBytes = bytesPerInt;
	}

	public PublicKey parsePublicKey(byte[] encodedKey)
			throws InvalidKeySpecException {
		if(encodedKey.length != publicKeyBytes)
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
		// FIXME: Verify that n times the point (x, y) = the point at infinity
		// Construct a public key from the point (x, y) and the params
		ECPoint pub = new ECPoint(x, y);
		ECPublicKeySpec keySpec = new ECPublicKeySpec(pub, params);
		ECPublicKey k = (ECPublicKey) keyFactory.generatePublic(keySpec);
		return new Sec1PublicKey(k, keyBits);
	}

	public PrivateKey parsePrivateKey(byte[] encodedKey)
			throws InvalidKeySpecException {
		if(encodedKey.length != privateKeyBytes)
			throw new InvalidKeySpecException();
		BigInteger s = new BigInteger(1, encodedKey); // Positive signum
		if(s.compareTo(params.getOrder()) >= 0)
			throw new InvalidKeySpecException();
		ECPrivateKeySpec keySpec = new ECPrivateKeySpec(s, params);
		ECPrivateKey k = (ECPrivateKey) keyFactory.generatePrivate(keySpec);
		return new Sec1PrivateKey(k, keyBits);
	}
}
