package net.sf.briar.transport;

import static javax.crypto.Cipher.ENCRYPT_MODE;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.security.GeneralSecurityException;

import javax.crypto.Cipher;

import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.util.ByteUtils;

class TagEncoder {

	static void encodeTag(byte[] tag, Cipher tagCipher, ErasableKey tagKey,
			long connection) {
		if(tag.length < TAG_LENGTH) throw new IllegalArgumentException();
		if(connection < 0 || connection > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		for(int i = 0; i < TAG_LENGTH; i++) tag[i] = 0;
		ByteUtils.writeUint32(connection, tag, 0);
		try {
			tagCipher.init(ENCRYPT_MODE, tagKey);
			int encrypted = tagCipher.doFinal(tag, 0, TAG_LENGTH, tag);
			if(encrypted != TAG_LENGTH) throw new IllegalArgumentException();
		} catch(GeneralSecurityException e) {
			// Unsuitable cipher or key
			throw new IllegalArgumentException(e);
		}
	}
}
