package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.crypto.ErasableKey;

class ConnectionEncrypterImpl implements ConnectionEncrypter {

	private final OutputStream out;
	private final Cipher frameCipher;
	private final ErasableKey frameKey;
	private final byte[] iv, tag;

	private long capacity, frame = 0L;
	private boolean tagWritten = false;

	ConnectionEncrypterImpl(OutputStream out, long capacity, Cipher tagCipher,
			Cipher frameCipher, ErasableKey tagKey, ErasableKey frameKey) {
		this.out = out;
		this.capacity = capacity;
		this.frameCipher = frameCipher;
		this.frameKey = frameKey;
		iv = IvEncoder.encodeIv(0, frameCipher.getBlockSize());
		// Encrypt the tag
		tag = TagEncoder.encodeTag(0, tagCipher, tagKey);
		tagKey.erase();
		if(tag.length != TAG_LENGTH) throw new IllegalArgumentException();
	}

	public void writeFrame(byte[] b, int off, int len) throws IOException {
		try {
			if(!tagWritten) {
				out.write(tag);
				capacity -= tag.length;
				tagWritten = true;
			}
			if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
			IvEncoder.updateIv(iv, frame);
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			try {
				frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
				int encrypted = frameCipher.doFinal(b, off, len, b, off);
				assert encrypted == len;
			} catch(GeneralSecurityException badCipher) {
				throw new RuntimeException(badCipher);
			}
			out.write(b, off, len);
			capacity -= len;
			frame++;
		} catch(IOException e) {
			frameKey.erase();
			throw e;
		}
	}

	public void flush() throws IOException {
		out.flush();
	}

	public long getRemainingCapacity() {
		return capacity;
	}
}