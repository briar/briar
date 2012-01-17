package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.security.GeneralSecurityException;

import javax.crypto.Cipher;

import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.util.ByteUtils;

class TagEncoder {

	static void encodeTag(byte[] tag, long segmentNumber, Cipher tagCipher,
			ErasableKey tagKey) {
		if(tag.length < TAG_LENGTH) throw new IllegalArgumentException();
		if(segmentNumber < 0 || segmentNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		// Clear the tag
		for(int i = 0; i < TAG_LENGTH; i++) tag[i] = 0;
		// Encode the segment number as a uint32 at the end of the tag
		ByteUtils.writeUint32(segmentNumber, tag, TAG_LENGTH - 4);
		try {
			tagCipher.init(Cipher.ENCRYPT_MODE, tagKey);
			int encrypted = tagCipher.doFinal(tag, 0, TAG_LENGTH, tag);
			if(encrypted != TAG_LENGTH) throw new IllegalArgumentException();
		} catch(GeneralSecurityException e) {
			// Unsuitable cipher or key
			throw new IllegalArgumentException(e);
		}
	}

	static long decodeTag(byte[] tag, Cipher tagCipher, ErasableKey tagKey) {
		if(tag.length < TAG_LENGTH) throw new IllegalArgumentException();
		try {
			tagCipher.init(Cipher.DECRYPT_MODE, tagKey);
			byte[] plaintext = tagCipher.doFinal(tag, 0, TAG_LENGTH);
			if(plaintext.length != TAG_LENGTH)
				throw new IllegalArgumentException();
			// All but the last four bytes of the plaintext should be blank
			for(int i = 0; i < TAG_LENGTH - 4; i++) {
				if(plaintext[i] != 0) return -1;
			}
			return ByteUtils.readUint32(plaintext, TAG_LENGTH - 4);
		} catch(GeneralSecurityException e) {
			// Unsuitable cipher or key
			throw new IllegalArgumentException(e);
		}
	}
}
