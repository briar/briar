package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.crypto.ErasableKey;

class OutgoingEncryptionLayerImpl implements FrameWriter {

	private final OutputStream out;
	private final Cipher tagCipher, frameCipher;
	private final ErasableKey tagKey, frameKey;
	private final byte[] iv, ciphertext;

	private long capacity, frameNumber;

	OutgoingEncryptionLayerImpl(OutputStream out, long capacity,
			Cipher tagCipher, Cipher frameCipher, ErasableKey tagKey,
			ErasableKey frameKey) {
		this.out = out;
		this.capacity = capacity;
		this.tagCipher = tagCipher;
		this.frameCipher = frameCipher;
		this.tagKey = tagKey;
		this.frameKey = frameKey;
		iv = IvEncoder.encodeIv(0L, frameCipher.getBlockSize());
		ciphertext = new byte[TAG_LENGTH + MAX_FRAME_LENGTH];
		frameNumber = 0L;
	}

	public void writeFrame(Frame f) throws IOException {
		byte[] plaintext = f.getBuffer();
		int length = f.getLength();
		int offset = 0;
		if(frameNumber == 0) {
			TagEncoder.encodeTag(ciphertext, tagCipher, tagKey);
			offset = TAG_LENGTH;
		}
		IvEncoder.updateIv(iv, frameNumber);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		try {
			frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
			int encrypted = frameCipher.doFinal(plaintext, 0, length,
					ciphertext, offset);
			if(encrypted != length) throw new RuntimeException();
		} catch(GeneralSecurityException badCipher) {
			throw new RuntimeException(badCipher);
		}
		try {
			out.write(ciphertext, 0, offset + length);
		} catch(IOException e) {
			frameKey.erase();
			tagKey.erase();
			throw e;
		}
		capacity -= offset + length;
		frameNumber++;
	}

	public void flush() throws IOException {
		out.flush();
	}

	public long getRemainingCapacity() {
		return capacity;
	}
}