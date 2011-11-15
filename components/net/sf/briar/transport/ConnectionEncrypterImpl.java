package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import net.sf.briar.api.crypto.ErasableKey;
import javax.crypto.spec.IvParameterSpec;

class ConnectionEncrypterImpl extends FilterOutputStream
implements ConnectionEncrypter {

	private final Cipher frameCipher;
	private final ErasableKey frameKey;
	private final byte[] iv, encryptedIv;

	private long capacity, frame = 0L;
	private boolean ivWritten = false, betweenFrames = false;

	ConnectionEncrypterImpl(OutputStream out, long capacity, byte[] iv,
			Cipher ivCipher, Cipher frameCipher, ErasableKey ivKey,
			ErasableKey frameKey) {
		super(out);
		this.capacity = capacity;
		this.iv = iv;
		this.frameCipher = frameCipher;
		this.frameKey = frameKey;
		// Encrypt the IV
		try {
			ivCipher.init(Cipher.ENCRYPT_MODE, ivKey);
			encryptedIv = ivCipher.doFinal(iv);
		} catch(BadPaddingException badCipher) {
			throw new IllegalArgumentException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new IllegalArgumentException(badCipher);
		} catch(InvalidKeyException badKey) {
			throw new IllegalArgumentException(badKey);
		}
		if(encryptedIv.length != IV_LENGTH)
			throw new IllegalArgumentException();
	}

	public OutputStream getOutputStream() {
		return this;
	}

	public void writeMac(byte[] mac) throws IOException {
		if(!ivWritten || betweenFrames) throw new IllegalStateException();
		try {
			out.write(frameCipher.doFinal(mac));
		} catch(BadPaddingException badCipher) {
			throw new RuntimeException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new RuntimeException(badCipher);
		}
		capacity -= mac.length;
		betweenFrames = true;
	}

	public long getRemainingCapacity() {
		return capacity;
	}

	@Override
	public void write(int b) throws IOException {
		if(!ivWritten) writeIv();
		if(betweenFrames) initialiseCipher();
		byte[] ciphertext = frameCipher.update(new byte[] {(byte) b});
		if(ciphertext != null) out.write(ciphertext);
		capacity--;
	}

	@Override
	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if(!ivWritten) writeIv();
		if(betweenFrames) initialiseCipher();
		byte[] ciphertext = frameCipher.update(b, off, len);
		if(ciphertext != null) out.write(ciphertext);
		capacity -= len;
	}

	private void writeIv() throws IOException {
		assert !ivWritten;
		assert !betweenFrames;
		out.write(encryptedIv);
		capacity -= encryptedIv.length;
		ivWritten = true;
		betweenFrames = true;
	}

	private void initialiseCipher() {
		assert ivWritten;
		assert betweenFrames;
		if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		IvEncoder.updateIv(iv, frame);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		try {
			frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		} catch(InvalidAlgorithmParameterException badIv) {
			throw new RuntimeException(badIv);
		} catch(InvalidKeyException badKey) {
			throw new RuntimeException(badKey);
		}
		frame++;
		betweenFrames = false;
	}
}