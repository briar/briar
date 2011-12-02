package net.sf.briar.transport;

import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.TransportConstants;
import net.sf.briar.util.ByteUtils;

class TagEncoder {

	static byte[] encodeTag(long frame, Cipher tagCipher, ErasableKey tagKey) {
		if(frame > ByteUtils.MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		// The plaintext is blank
		byte[] plaintext = new byte[TransportConstants.TAG_LENGTH];
		// Encode the frame number as a uint32 at the end of the IV
		byte[] iv = new byte[tagCipher.getBlockSize()];
		if(iv.length != plaintext.length) throw new IllegalArgumentException();
		ByteUtils.writeUint32(frame, iv, iv.length - 4);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		try {
			tagCipher.init(Cipher.ENCRYPT_MODE, tagKey, ivSpec);
			return tagCipher.doFinal(plaintext);
		} catch(GeneralSecurityException e) {
			// Unsuitable cipher or key
			throw new IllegalArgumentException(e);
		}
	}

	static boolean validateTag(byte[] tag, long frame, Cipher tagCipher,
			ErasableKey tagKey) {
		if(tag.length != TransportConstants.TAG_LENGTH) return false;
		// Encode the frame number as a uint32 at the end of the IV
		byte[] iv = new byte[tagCipher.getBlockSize()];
		if(iv.length != tag.length) throw new IllegalArgumentException();
		ByteUtils.writeUint32(frame, iv, iv.length - 4);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		try {
			tagCipher.init(Cipher.DECRYPT_MODE, tagKey, ivSpec);
			byte[] plaintext = tagCipher.doFinal(tag);
			// The plaintext should be blank
			for(int i = 0; i < plaintext.length; i++) {
				if(plaintext[i] != 0) return false;
			}
			return true;
		} catch(GeneralSecurityException e) {
			// Unsuitable cipher or key
			throw new IllegalArgumentException(e);
		}
	}
}
