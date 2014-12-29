package org.briarproject.crypto;

import java.security.GeneralSecurityException;

import org.briarproject.api.crypto.AuthenticatedCipher;
import org.briarproject.api.crypto.SecretKey;
import org.spongycastle.crypto.DataLengthException;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.modes.AEADBlockCipher;
import org.spongycastle.crypto.params.AEADParameters;
import org.spongycastle.crypto.params.KeyParameter;

class AuthenticatedCipherImpl implements AuthenticatedCipher {

	private final AEADBlockCipher cipher;
	private final int macLength;

	AuthenticatedCipherImpl(AEADBlockCipher cipher, int macLength) {
		this.cipher = cipher;
		this.macLength = macLength;
	}

	public int doFinal(byte[] input, int inputOff, int len, byte[] output,
			int outputOff) throws GeneralSecurityException {
		int processed = 0;
		if(len != 0) {
			processed = cipher.processBytes(input, inputOff, len, output,
					outputOff);
		}
		try {
			return processed + cipher.doFinal(output, outputOff + processed);
		} catch(DataLengthException e) {
			throw new GeneralSecurityException(e.getMessage());
		} catch(InvalidCipherTextException e) {
			throw new GeneralSecurityException(e.getMessage());
		}
	}

	public void init(boolean encrypt, SecretKey key, byte[] iv, byte[] aad)
			throws GeneralSecurityException {
		KeyParameter k = new KeyParameter(key.getBytes());
		AEADParameters params = new AEADParameters(k, macLength * 8, iv, aad);
		try {
			cipher.init(encrypt, params);
		} catch(IllegalArgumentException e) {
			throw new GeneralSecurityException(e.getMessage());
		}
	}

	public int getMacLength() {
		return macLength;
	}

	public int getBlockSize() {
		return cipher.getUnderlyingCipher().getBlockSize();
	}
}
