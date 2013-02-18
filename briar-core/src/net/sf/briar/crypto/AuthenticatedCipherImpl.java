package net.sf.briar.crypto;

import java.security.InvalidKeyException;
import java.security.Key;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;

import net.sf.briar.api.crypto.AuthenticatedCipher;

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
			int outputOff) throws IllegalBlockSizeException,
			BadPaddingException {
		int processed = 0;
		if(len != 0) {
			processed = cipher.processBytes(input, inputOff, len, output,
					outputOff);
		}
		try {
			return processed + cipher.doFinal(output, outputOff + processed);
		} catch(DataLengthException e) {
			throw new IllegalBlockSizeException(e.getMessage());
		} catch(InvalidCipherTextException e) {
			throw new BadPaddingException(e.getMessage());
		}
	}

	public void init(int opmode, Key key, byte[] iv, byte[] aad)
			throws InvalidKeyException {
		KeyParameter k = new KeyParameter(key.getEncoded());
		AEADParameters params = new AEADParameters(k, macLength * 8, iv, aad);
		try {
			switch(opmode) {
			case Cipher.ENCRYPT_MODE:
			case Cipher.WRAP_MODE:
				cipher.init(true, params);
				break;
			case Cipher.DECRYPT_MODE:
			case Cipher.UNWRAP_MODE:
				cipher.init(false, params);
				break;
			default:
				throw new IllegalArgumentException();
			}
		} catch(Exception e) {
			throw new InvalidKeyException(e.getMessage());
		}
	}

	public int getMacLength() {
		return macLength;
	}

	public int getBlockSize() {
		return cipher.getUnderlyingCipher().getBlockSize();
	}
}
