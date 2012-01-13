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
	private final Cipher tagCipher, frameCipher;
	private final ErasableKey tagKey, frameKey;
	private final boolean tagEverySegment;
	private final byte[] iv, tag;

	private long capacity, frame = 0L;

	ConnectionEncrypterImpl(OutputStream out, long capacity, Cipher tagCipher,
			Cipher frameCipher, ErasableKey tagKey, ErasableKey frameKey,
			boolean tagEverySegment) {
		this.out = out;
		this.capacity = capacity;
		this.tagCipher = tagCipher;
		this.frameCipher = frameCipher;
		this.tagKey = tagKey;
		this.frameKey = frameKey;
		this.tagEverySegment = tagEverySegment;
		iv = IvEncoder.encodeIv(0, frameCipher.getBlockSize());
		tag = new byte[TAG_LENGTH];
	}

	public void writeFrame(byte[] b, int len) throws IOException {
		if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		try {
			if(tagEverySegment || frame == 0) {
				TagEncoder.encodeTag(tag, frame, tagCipher, tagKey);
				out.write(tag);
				capacity -= tag.length;
			}
			IvEncoder.updateIv(iv, frame);
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			try {
				frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
				int encrypted = frameCipher.doFinal(b, 0, len, b);
				if(encrypted != len) throw new RuntimeException();
			} catch(GeneralSecurityException badCipher) {
				throw new RuntimeException(badCipher);
			}
			out.write(b, 0, len);
			capacity -= len;
			frame++;
		} catch(IOException e) {
			frameKey.erase();
			tagKey.erase();
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