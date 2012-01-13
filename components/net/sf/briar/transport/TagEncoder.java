package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.util.ByteUtils;

class TagEncoder {

	private static final byte[] BLANK = new byte[TAG_LENGTH];

	static void encodeTag(byte[] tag, long frame, Cipher tagCipher,
			ErasableKey tagKey) {
		if(tag.length < TAG_LENGTH) throw new IllegalArgumentException();
		if(frame < 0 || frame > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		// Encode the frame number as a uint32 at the end of the IV
		byte[] iv = new byte[tagCipher.getBlockSize()];
		if(iv.length != TAG_LENGTH) throw new IllegalArgumentException();
		ByteUtils.writeUint32(frame, iv, iv.length - 4);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		try {
			tagCipher.init(Cipher.ENCRYPT_MODE, tagKey, ivSpec);
			int encrypted = tagCipher.doFinal(BLANK, 0, TAG_LENGTH, tag);
			if(encrypted != TAG_LENGTH) throw new IllegalArgumentException();
		} catch(GeneralSecurityException e) {
			// Unsuitable cipher or key
			throw new IllegalArgumentException(e);
		}
	}

	static boolean validateTag(byte[] tag, long frame, Cipher tagCipher,
			ErasableKey tagKey) {
		if(frame < 0 || frame > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		if(tag.length < TAG_LENGTH) return false;
		// Encode the frame number as a uint32 at the end of the IV
		byte[] iv = new byte[tagCipher.getBlockSize()];
		if(iv.length != TAG_LENGTH) throw new IllegalArgumentException();
		ByteUtils.writeUint32(frame, iv, iv.length - 4);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		try {
			tagCipher.init(Cipher.DECRYPT_MODE, tagKey, ivSpec);
			byte[] plaintext = tagCipher.doFinal(tag, 0, TAG_LENGTH);
			if(plaintext.length != TAG_LENGTH)
				throw new IllegalArgumentException();
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
