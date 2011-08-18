package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
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

	private final int transportId;
	private final long connection;
	private final Cipher tagCipher, frameCipher;
	private final SecretKey frameKey;
	private final byte[] tag;

	private long frame = 0L;
	private boolean started = false, betweenFrames = false;

	ConnectionEncrypterImpl(OutputStream out, int transportId,
			long connection, Cipher tagCipher, Cipher frameCipher,
			SecretKey tagKey, SecretKey frameKey) {
		super(out);
		this.transportId = transportId;
		this.connection = connection;
		this.tagCipher = tagCipher;
		this.frameCipher = frameCipher;
		this.frameKey = frameKey;
		tag = new byte[TAG_LENGTH];
		try {
			tagCipher.init(Cipher.ENCRYPT_MODE, tagKey);
		} catch(InvalidKeyException badKey) {
			throw new IllegalArgumentException(badKey);
		}
		if(tagCipher.getOutputSize(TAG_LENGTH) != TAG_LENGTH)
			throw new IllegalArgumentException();
	}

	public OutputStream getOutputStream() {
		return this;
	}

	public void writeMac(byte[] mac) throws IOException {
		if(!started || betweenFrames) throw new IllegalStateException();
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
		if(!started) writeTag();
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
		if(!started) writeTag();
		if(betweenFrames) initialiseCipher();
		byte[] ciphertext = frameCipher.update(b, off, len);
		if(ciphertext != null) out.write(ciphertext);
	}

	private void writeTag() throws IOException {
		assert !started;
		assert !betweenFrames;
		TagEncoder.encodeTag(tag, transportId, connection, 0L);
		try {
			out.write(tagCipher.doFinal(tag));
		} catch(BadPaddingException badCipher) {
			throw new IOException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new RuntimeException(badCipher);
		}
		started = true;
		betweenFrames = true;
	}

	private void initialiseCipher() {
		assert started;
		assert betweenFrames;
		if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		TagEncoder.encodeTag(tag, transportId, connection, frame);
		IvParameterSpec iv = new IvParameterSpec(tag);
		try {
			frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, iv);
		} catch(InvalidAlgorithmParameterException badIv) {
			throw new RuntimeException(badIv);
		} catch(InvalidKeyException badKey) {
			throw new RuntimeException(badKey);
		}
		frame++;
		betweenFrames = false;
	}
}