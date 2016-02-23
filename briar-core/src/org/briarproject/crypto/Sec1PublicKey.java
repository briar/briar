package org.briarproject.crypto;

import org.briarproject.api.crypto.PublicKey;
import org.spongycastle.crypto.params.ECPublicKeyParameters;

/**
 * An elliptic curve public key that uses the encoding defined in "SEC 1:
 * Elliptic Curve Cryptography", section 2.3 (Certicom Corporation, May 2009).
 * Point compression is not used.
 */
class Sec1PublicKey implements PublicKey {

	private final ECPublicKeyParameters key;

	Sec1PublicKey(ECPublicKeyParameters key) {
		this.key = key;
	}

	public byte[] getEncoded() {
		return key.getQ().getEncoded(false);
	}

	ECPublicKeyParameters getKey() {
		return key;
	}
}
