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
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

class ConnectionEncrypterImpl extends FilterOutputStream
implements ConnectionEncrypter {

	private final Cipher ivCipher, frameCipher;
	private final SecretKey frameKey;
	private final byte[] iv;

	private long frame = 0L;
	private boolean ivWritten = false, betweenFrames = false;

	ConnectionEncrypterImpl(OutputStream out, boolean initiator,
			int transportId, long connection, Cipher ivCipher,
			Cipher frameCipher, SecretKey ivKey, SecretKey frameKey) {
		super(out);
		this.ivCipher = ivCipher;
		this.frameCipher = frameCipher;
		this.frameKey = frameKey;
		iv = IvEncoder.encodeIv(initiator, transportId, connection);
		try {
			ivCipher.init(Cipher.ENCRYPT_MODE, ivKey);
		} catch(InvalidKeyException badKey) {
			throw new IllegalArgumentException(badKey);
		}
		if(ivCipher.getOutputSize(IV_LENGTH) != IV_LENGTH)
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
			throw new IOException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new RuntimeException(badCipher);
		}
		betweenFrames = true;
	}

	@Override
	public void write(int b) throws IOException {
		if(!ivWritten) writeIv();
		if(betweenFrames) initialiseCipher();
		byte[] ciphertext = frameCipher.update(new byte[] {(byte) b});
		if(ciphertext != null) out.write(ciphertext);
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
	}

	private void writeIv() throws IOException {
		assert !ivWritten;
		assert !betweenFrames;
		try {
			out.write(ivCipher.doFinal(iv));
		} catch(BadPaddingException badCipher) {
			throw new IOException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new RuntimeException(badCipher);
		}
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