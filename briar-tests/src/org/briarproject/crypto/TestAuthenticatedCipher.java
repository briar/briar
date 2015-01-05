package org.briarproject.crypto;

import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;

import java.security.GeneralSecurityException;

import org.briarproject.api.crypto.AuthenticatedCipher;
import org.briarproject.api.crypto.SecretKey;

class TestAuthenticatedCipher implements AuthenticatedCipher {

	private static final int BLOCK_BYTES = 16;

	private boolean encrypt = false;

	public void init(boolean encrypt, SecretKey key, byte[] iv, byte[] aad)
			throws GeneralSecurityException {
		this.encrypt = encrypt;
	}

	public int process(byte[] input, int inputOff, int len, byte[] output,
			int outputOff) throws GeneralSecurityException {
		if(encrypt) {
			System.arraycopy(input, inputOff, output, outputOff, len);
			for(int i = 0; i < MAC_LENGTH; i++)
				output[outputOff + len + i] = 0;
			return len + MAC_LENGTH;
		} else {
			for(int i = 0; i < MAC_LENGTH; i++)
				if(input[inputOff + len - MAC_LENGTH + i] != 0)
					throw new GeneralSecurityException();
			System.arraycopy(input, inputOff, output, outputOff,
					len - MAC_LENGTH);
			return len - MAC_LENGTH;
		}
	}

	public int getMacLength() {
		return MAC_LENGTH;
	}

	public int getBlockSize() {
		return BLOCK_BYTES;
	}
}
