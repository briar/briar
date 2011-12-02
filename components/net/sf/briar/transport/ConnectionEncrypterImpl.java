package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.crypto.ErasableKey;

class ConnectionEncrypterImpl extends FilterOutputStream
implements ConnectionEncrypter {

	private final Cipher frameCipher;
	private final ErasableKey frameKey;
	private final byte[] iv, tag;

	private long capacity, frame = 0L;
	private boolean tagWritten = false, betweenFrames = false;

	ConnectionEncrypterImpl(OutputStream out, long capacity, Cipher tagCipher,
			Cipher frameCipher, ErasableKey tagKey, ErasableKey frameKey) {
		super(out);
		this.capacity = capacity;
		this.frameCipher = frameCipher;
		this.frameKey = frameKey;
		iv = IvEncoder.encodeIv(0, frameCipher.getBlockSize());
		// Encrypt the tag
		tag = TagEncoder.encodeTag(0, tagCipher, tagKey);
		tagKey.erase();
		if(tag.length != TAG_LENGTH) throw new IllegalArgumentException();
	}

	public OutputStream getOutputStream() {
		return this;
	}

	public void writeFinal(byte[] b) throws IOException {
		try {
			if(!tagWritten || betweenFrames) throw new IllegalStateException();
			try {
				out.write(frameCipher.doFinal(b));
			} catch(GeneralSecurityException badCipher) {
				throw new RuntimeException(badCipher);
			}
			capacity -= b.length;
			betweenFrames = true;
		} catch(IOException e) {
			frameKey.erase();
			throw e;
		}
	}

	public long getRemainingCapacity() {
		return capacity;
	}

	@Override
	public void write(int b) throws IOException {
		try {
			if(!tagWritten) writeTag();
			if(betweenFrames) initialiseCipher();
			byte[] ciphertext = frameCipher.update(new byte[] {(byte) b});
			if(ciphertext != null) out.write(ciphertext);
			capacity--;
		} catch(IOException e) {
			frameKey.erase();
			throw e;
		}
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		try {
			if(!tagWritten) writeTag();
			if(betweenFrames) initialiseCipher();
			byte[] ciphertext = frameCipher.update(b, off, len);
			if(ciphertext != null) out.write(ciphertext);
			capacity -= len;
		} catch(IOException e) {
			frameKey.erase();
			throw e;
		}
	}

	private void writeTag() throws IOException {
		assert !tagWritten;
		assert !betweenFrames;
		out.write(tag);
		capacity -= tag.length;
		tagWritten = true;
		betweenFrames = true;
	}

	private void initialiseCipher() {
		assert tagWritten;
		assert betweenFrames;
		if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		IvEncoder.updateIv(iv, frame);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		try {
			frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		} catch(GeneralSecurityException badIvOrKey) {
			throw new RuntimeException(badIvOrKey);
		}
		frame++;
		betweenFrames = false;
	}
}