package net.sf.briar.crypto;

import java.math.BigInteger;
import java.security.GeneralSecurityException;

import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.crypto.PrivateKey;
import net.sf.briar.api.crypto.PublicKey;

import org.spongycastle.crypto.params.ECDomainParameters;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.math.ec.ECFieldElement;
import org.spongycastle.math.ec.ECPoint;

/**
 * A key parser that uses the encoding defined in "SEC 1: Elliptic Curve
 * Cryptography", section 2.3 (Certicom Corporation, May 2009). Point
 * compression is not used.
 */
class Sec1KeyParser implements KeyParser {

	private final ECDomainParameters params;
	private final BigInteger modulus;
	private final int keyBits, bytesPerInt, publicKeyBytes, privateKeyBytes;

	Sec1KeyParser(ECDomainParameters params, BigInteger modulus, int keyBits) {
		this.params = params;
		this.modulus = modulus;
		this.keyBits = keyBits;
		bytesPerInt = (keyBits + 7) / 8;
		publicKeyBytes = 1 + 2 * bytesPerInt;
		privateKeyBytes = bytesPerInt;
	}

	public PublicKey parsePublicKey(byte[] encodedKey)
			throws GeneralSecurityException {
		// The validation procedure comes from SEC 1, section 3.2.2.1. Note
		// that SEC 1 parameter names are used below, not RFC 5639 names
		if(encodedKey.length != publicKeyBytes)
			throw new GeneralSecurityException();
		// The first byte must be 0x04
		if(encodedKey[0] != 4) throw new GeneralSecurityException();
		// The x co-ordinate must be >= 0 and < p
		byte[] xBytes = new byte[bytesPerInt];
		System.arraycopy(encodedKey, 1, xBytes, 0, bytesPerInt);
		BigInteger x = new BigInteger(1, xBytes); // Positive signum
		if(x.compareTo(modulus) >= 0) throw new GeneralSecurityException();
		// The y co-ordinate must be >= 0 and < p
		byte[] yBytes = new byte[bytesPerInt];
		System.arraycopy(encodedKey, 1 + bytesPerInt, yBytes, 0, bytesPerInt);
		BigInteger y = new BigInteger(1, yBytes); // Positive signum
		if(y.compareTo(modulus) >= 0) throw new GeneralSecurityException();
		// Verify that y^2 == x^3 + ax + b (mod p)
		BigInteger a = params.getCurve().getA().toBigInteger();
		BigInteger b = params.getCurve().getB().toBigInteger();
		BigInteger lhs = y.multiply(y).mod(modulus);
		BigInteger rhs = x.multiply(x).add(a).multiply(x).add(b).mod(modulus);
		if(!lhs.equals(rhs)) throw new GeneralSecurityException();
		// We know the point (x, y) is on the curve, so we can create the point
		ECFieldElement elementX = new ECFieldElement.Fp(modulus, x);
		ECFieldElement elementY = new ECFieldElement.Fp(modulus, y);
		ECPoint pub = new ECPoint.Fp(params.getCurve(), elementX, elementY);
		// Verify that the point (x, y) is not the point at infinity
		if(pub.isInfinity()) throw new GeneralSecurityException();
		// Verify that the point (x, y) times n is the point at infinity
		if(!pub.multiply(params.getN()).isInfinity())
			throw new GeneralSecurityException();
		// Construct a public key from the point (x, y) and the params
		ECPublicKeyParameters k = new ECPublicKeyParameters(pub, params);
		return new Sec1PublicKey(k, keyBits);
	}

	public PrivateKey parsePrivateKey(byte[] encodedKey)
			throws GeneralSecurityException {
		if(encodedKey.length != privateKeyBytes)
			throw new GeneralSecurityException();
		BigInteger d = new BigInteger(1, encodedKey); // Positive signum
		// Verify that the private value is < n
		if(d.compareTo(params.getN()) >= 0)
			throw new GeneralSecurityException();
		// Construct a private key from the private value and the params
		ECPrivateKeyParameters k = new ECPrivateKeyParameters(d, params);
		return new Sec1PrivateKey(k, keyBits);
	}
}
