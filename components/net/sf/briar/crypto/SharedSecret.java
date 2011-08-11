package net.sf.briar.crypto;

import java.util.Arrays;

import javax.crypto.spec.IvParameterSpec;

class SharedSecret {

	private static final int IV_BYTES = 16;

	private final IvParameterSpec iv;
	private final boolean alice;
	private final byte[] ciphertext;

	SharedSecret(byte[] secret) {
		if(secret.length < IV_BYTES + 2) throw new IllegalArgumentException();
		iv = new IvParameterSpec(secret, 0, IV_BYTES);
		switch(secret[IV_BYTES]) {
		case 0:
			alice = false;
			break;
		case 1:
			alice = true;
			break;
		default:
			throw new IllegalArgumentException();
		}
		ciphertext = Arrays.copyOfRange(secret, IV_BYTES + 1, secret.length);
	}

	IvParameterSpec getIv() {
		return iv;
	}

	boolean getAlice() {
		return alice;
	}

	byte[] getCiphertext() {
		return ciphertext;
	}
}
