package org.briarproject.bramble.crypto;

import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An elliptic curve public key that uses the encoding defined in "SEC 1:
 * Elliptic Curve Cryptography", section 2.3 (Certicom Corporation, May 2009).
 * Point compression is not used.
 */
@Immutable
@NotNullByDefault
class Sec1PublicKey implements PublicKey {

	private final String keyType;
	private final ECPublicKeyParameters key;

	Sec1PublicKey(String keyType, ECPublicKeyParameters key) {
		this.keyType = keyType;
		this.key = key;
	}

	@Override
	public String getKeyType() {
		return keyType;
	}

	@Override
	public byte[] getEncoded() {
		return key.getQ().getEncoded(false);
	}

	ECPublicKeyParameters getKey() {
		return key;
	}
}
