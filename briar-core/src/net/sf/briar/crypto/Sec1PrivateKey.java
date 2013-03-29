package net.sf.briar.crypto;

import java.math.BigInteger;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECParameterSpec;

class Sec1PrivateKey implements ECPrivateKey,
org.spongycastle.jce.interfaces.ECPrivateKey {

	private static final long serialVersionUID = -493100835871466670L;

	private final ECPrivateKey key;
	private final int privateKeyBytes;

	Sec1PrivateKey(ECPrivateKey key, int keyBits) {
		// Spongy Castle only accepts instances of its own interface, so we
		// have to wrap an instance of that interface and delegate to it
		if(!(key instanceof org.spongycastle.jce.interfaces.ECPrivateKey))
			throw new IllegalArgumentException();
		this.key = key;
		privateKeyBytes = (int) Math.ceil(keyBits / 8.0);
	}

	public String getAlgorithm() {
		return key.getAlgorithm();
	}

	public byte[] getEncoded() {
		byte[] encodedKey = new byte[privateKeyBytes];
		BigInteger s = key.getS();
		// Copy up to privateKeyBytes bytes into exactly privateKeyBytes bytes
		byte[] sBytes = s.toByteArray();
		for(int i = 0; i < sBytes.length && i < privateKeyBytes; i++)
			encodedKey[privateKeyBytes - 1 - i] = sBytes[sBytes.length - 1 - i];
		return encodedKey;
	}

	public String getFormat() {
		return "SEC1";
	}

	public ECParameterSpec getParams() {
		return key.getParams();
	}

	public BigInteger getS() {
		return key.getS();
	}

	public org.spongycastle.jce.spec.ECParameterSpec getParameters() {
		return ((org.spongycastle.jce.interfaces.ECPrivateKey) key).getParameters();
	}

	public BigInteger getD() {
		return ((org.spongycastle.jce.interfaces.ECPrivateKey) key).getD();
	}
}
