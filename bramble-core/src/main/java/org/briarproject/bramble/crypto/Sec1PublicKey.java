package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.spongycastle.crypto.params.ECPublicKeyParameters;

import javax.annotation.concurrent.Immutable;

/**
 * An elliptic curve public key that uses the encoding defined in "SEC 1:
 * Elliptic Curve Cryptography", section 2.3 (Certicom Corporation, May 2009).
 * Point compression is not used.
 */
@Immutable
@NotNullByDefault
class Sec1PublicKey implements PublicKey {

	private final ECPublicKeyParameters key;

	Sec1PublicKey(ECPublicKeyParameters key) {
		this.key = key;
	}

	@Override
	public byte[] getEncoded() {
		return key.getQ().getEncoded(false);
	}

	ECPublicKeyParameters getKey() {
		return key;
	}
}
