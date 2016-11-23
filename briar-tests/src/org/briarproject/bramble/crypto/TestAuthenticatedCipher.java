package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.security.GeneralSecurityException;

import static org.briarproject.bramble.api.transport.TransportConstants.MAC_LENGTH;

@NotNullByDefault
class TestAuthenticatedCipher implements AuthenticatedCipher {

	private boolean encrypt = false;

	@Override
	public void init(boolean encrypt, SecretKey key, byte[] iv)
			throws GeneralSecurityException {
		this.encrypt = encrypt;
	}

	@Override
	public int process(byte[] input, int inputOff, int len, byte[] output,
			int outputOff) throws GeneralSecurityException {
		if (encrypt) {
			System.arraycopy(input, inputOff, output, outputOff, len);
			for (int i = 0; i < MAC_LENGTH; i++)
				output[outputOff + len + i] = 0;
			return len + MAC_LENGTH;
		} else {
			for (int i = 0; i < MAC_LENGTH; i++)
				if (input[inputOff + len - MAC_LENGTH + i] != 0)
					throw new GeneralSecurityException();
			System.arraycopy(input, inputOff, output, outputOff,
					len - MAC_LENGTH);
			return len - MAC_LENGTH;
		}
	}

	@Override
	public int getMacBytes() {
		return MAC_LENGTH;
	}
}
