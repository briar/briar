package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.crypto.IvEncoder;

class OutgoingEncryptionLayerImpl implements FrameWriter {

	private final OutputStream out;
	private final Cipher tagCipher, frameCipher;
	private final IvEncoder frameIvEncoder;
	private final ErasableKey tagKey, frameKey;
	private final byte[] frameIv, ciphertext;

	private long capacity, frameNumber;

	OutgoingEncryptionLayerImpl(OutputStream out, long capacity,
			Cipher tagCipher, Cipher frameCipher, IvEncoder frameIvEncoder,
			ErasableKey tagKey, ErasableKey frameKey) {
		this.out = out;
		this.capacity = capacity;
		this.tagCipher = tagCipher;
		this.frameCipher = frameCipher;
		this.frameIvEncoder = frameIvEncoder;
		this.tagKey = tagKey;
		this.frameKey = frameKey;
		frameIv = frameIvEncoder.encodeIv(0L);
		ciphertext = new byte[TAG_LENGTH + MAX_FRAME_LENGTH];
		frameNumber = 0L;
	}

	public void writeFrame(Frame f) throws IOException {
		byte[] plaintext = f.getBuffer();
		int offset = 0, length = f.getLength();
		if(frameNumber == 0) {
			TagEncoder.encodeTag(ciphertext, tagCipher, tagKey);
			offset = TAG_LENGTH;
		}
		frameIvEncoder.updateIv(frameIv, frameNumber);
		IvParameterSpec ivSpec = new IvParameterSpec(frameIv);
		try {
			frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
			int encrypted = frameCipher.doFinal(plaintext, 0, length,
					ciphertext, offset);
			if(encrypted != length + MAC_LENGTH) throw new RuntimeException();
		} catch(GeneralSecurityException badCipher) {
			throw new RuntimeException(badCipher);
		}
		try {
			out.write(ciphertext, 0, offset + length + MAC_LENGTH);
		} catch(IOException e) {
			frameKey.erase();
			tagKey.erase();
			throw e;
		}
		capacity -= offset + length + MAC_LENGTH;
		frameNumber++;
	}

	public void flush() throws IOException {
		out.flush();
	}

	public long getRemainingCapacity() {
		return capacity;
	}
}