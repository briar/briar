package net.sf.briar.crypto;

/**
 * A shared secret from which authentication and encryption keys can be derived.
 * The secret carries a flag indicating whether Alice's keys or Bob's keys
 * should be derived from the secret. When two parties agree on a shared secret,
 * they must decide which of them will derive Alice's keys and which Bob's.
 */
class SharedSecret {

	private final boolean alice;
	private final byte[] secret;

	SharedSecret(byte[] b) {
		if(b.length < 2) throw new IllegalArgumentException();
		switch(b[0]) {
		case 0:
			alice = false;
			break;
		case 1:
			alice = true;
			break;
		default:
			throw new IllegalArgumentException();
		}
		secret = new byte[b.length - 1];
		System.arraycopy(b, 1, secret, 0, secret.length);
	}

	SharedSecret(boolean alice, byte[] secret) {
		this.alice = alice;
		this.secret = secret;
	}

	/**
	 * Returns true if we should play the role of Alice in connections using
	 * this secret, or false if we should play the role of Bob.
	 */
	boolean getAlice() {
		return alice;
	}

	/** Returns the shared secret. */
	byte[] getSecret() {
		return secret;
	}

	/**
	 * Returns a raw representation of this object, suitable for storing in the
	 * database.
	 */
	byte[] getBytes() {
		byte[] b = new byte[1 + secret.length];
		if(alice) b[0] = (byte) 1;
		System.arraycopy(secret, 0, b, 1, secret.length);
		return b;
	}
}
