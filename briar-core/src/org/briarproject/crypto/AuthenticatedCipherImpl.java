package org.briarproject.crypto;

import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;

import java.security.GeneralSecurityException;

import org.briarproject.api.crypto.SecretKey;
import org.spongycastle.crypto.DataLengthException;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.engines.AESLightEngine;
import org.spongycastle.crypto.modes.AEADBlockCipher;
import org.spongycastle.crypto.modes.GCMBlockCipher;
import org.spongycastle.crypto.modes.gcm.BasicGCMMultiplier;
import org.spongycastle.crypto.params.AEADParameters;
import org.spongycastle.crypto.params.KeyParameter;

class AuthenticatedCipherImpl implements AuthenticatedCipher {

	private final AEADBlockCipher cipher;

	AuthenticatedCipherImpl() {
		cipher = new GCMBlockCipher(new AESLightEngine(),
				new BasicGCMMultiplier());
	}

	public int process(byte[] input, int inputOff, int len, byte[] output,
			int outputOff) throws GeneralSecurityException {
		int processed = 0;
		if (len != 0) {
			processed = cipher.processBytes(input, inputOff, len, output,
					outputOff);
		}
		try {
			return processed + cipher.doFinal(output, outputOff + processed);
		} catch (DataLengthException e) {
			throw new GeneralSecurityException(e.getMessage());
		} catch (InvalidCipherTextException e) {
			throw new GeneralSecurityException(e.getMessage());
		}
	}

	public void init(boolean encrypt, SecretKey key, byte[] iv)
			throws GeneralSecurityException {
		KeyParameter k = new KeyParameter(key.getBytes());
		// Authenticate the IV by passing it as additional authenticated data
		AEADParameters params = new AEADParameters(k, MAC_LENGTH * 8, iv, iv);
		try {
			cipher.init(encrypt, params);
		} catch (IllegalArgumentException e) {
			throw new GeneralSecurityException(e.getMessage());
		}
	}

	public int getMacBytes() {
		return MAC_LENGTH;
	}
}
