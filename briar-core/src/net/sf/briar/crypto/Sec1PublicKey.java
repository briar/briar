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
class Sec1PublicKey implements ECPublicKey,
org.spongycastle.jce.interfaces.ECPublicKey {

	private static final long serialVersionUID = -2722797033851423987L;

	private final ECPublicKey key;
	private final int bytesPerInt, publicKeyBytes;

	Sec1PublicKey(ECPublicKey key, int keyBits) {
		// Spongy Castle only accepts instances of its own interface, so we
		// have to wrap an instance of that interface and delegate to it
		if(!(key instanceof org.spongycastle.jce.interfaces.ECPublicKey))
			throw new IllegalArgumentException();
		this.key = key;
		bytesPerInt = (int) Math.ceil(keyBits / 8.0);
		publicKeyBytes = 1 + 2 * bytesPerInt;
	}

	public String getAlgorithm() {
		return key.getAlgorithm();
	}

	public byte[] getEncoded() {
		byte[] encodedKey = new byte[publicKeyBytes];
		encodedKey[0] = 4;
		BigInteger x = key.getW().getAffineX();
		BigInteger y = key.getW().getAffineY();
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

	public org.spongycastle.jce.spec.ECParameterSpec getParameters() {
		return ((org.spongycastle.jce.interfaces.ECPublicKey) key).getParameters();
	}

	public org.spongycastle.math.ec.ECPoint getQ() {
		return ((org.spongycastle.jce.interfaces.ECPublicKey) key).getQ();
	}
}
