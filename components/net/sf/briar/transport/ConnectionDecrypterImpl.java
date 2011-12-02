package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import net.sf.briar.api.crypto.ErasableKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;

class ConnectionDecrypterImpl extends FilterInputStream
implements ConnectionDecrypter {

	private final Cipher frameCipher;
	private final ErasableKey frameKey;
	private final byte[] iv, buf;

	private int bufOff = 0, bufLen = 0;
	private long frame = 0L;
	private boolean betweenFrames = true;

	ConnectionDecrypterImpl(InputStream in, byte[] iv, Cipher frameCipher,
			ErasableKey frameKey) {
		super(in);
		if(iv.length != TAG_LENGTH) throw new IllegalArgumentException();
		this.iv = iv;
		this.frameCipher = frameCipher;
		this.frameKey = frameKey;
		buf = new byte[TAG_LENGTH];
	}

	public InputStream getInputStream() {
		return this;
	}

	public void readMac(byte[] mac) throws IOException {
		try {
			if(betweenFrames) throw new IllegalStateException();
			// If we have any plaintext in the buffer, copy it into the MAC
			System.arraycopy(buf, bufOff, mac, 0, bufLen);
			// Read the remainder of the MAC
			int offset = bufLen;
			while(offset < mac.length) {
				int read = in.read(mac, offset, mac.length - offset);
				if(read == -1) break;
				offset += read;
			}
			if(offset < mac.length) throw new EOFException(); // Unexpected EOF
			// Decrypt the remainder of the MAC
			try {
				int length = mac.length - bufLen;
				int i = frameCipher.doFinal(mac, bufLen, length, mac, bufLen);
				if(i < length) throw new RuntimeException();
			} catch(BadPaddingException badCipher) {
				throw new RuntimeException(badCipher);
			} catch(IllegalBlockSizeException badCipher) {
				throw new RuntimeException(badCipher);
			} catch(ShortBufferException badCipher) {
				throw new RuntimeException(badCipher);
			}
			bufOff = bufLen = 0;
			betweenFrames = true;
		} catch(IOException e) {
			frameKey.erase();
			throw e;
		}
	}

	@Override
	public int read() throws IOException {
		try {
			if(betweenFrames) initialiseCipher();
			if(bufLen == 0) {
				if(!readBlock()) {
					frameKey.erase();
					return -1;
				}
				bufOff = 0;
				bufLen = buf.length;
			}
			int i = buf[bufOff];
			bufOff++;
			bufLen--;
			return i < 0 ? i + 256 : i;
		} catch(IOException e) {
			frameKey.erase();
			throw e;
		}
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		try {
			if(betweenFrames) initialiseCipher();
			if(bufLen == 0) {
				if(!readBlock()) {
					frameKey.erase();
					return -1;
				}
				bufOff = 0;
				bufLen = buf.length;
			}
			int length = Math.min(len, bufLen);
			System.arraycopy(buf, bufOff, b, off, length);
			bufOff += length;
			bufLen -= length;
			return length;
		} catch(IOException e) {
			frameKey.erase();
			throw e;
		}
	}

	// Although we're using CTR mode, which doesn't require full blocks of
	// ciphertext, the cipher still tries to operate a block at a time
	private boolean readBlock() throws IOException {
		// Try to read a block of ciphertext
		int offset = 0;
		while(offset < buf.length) {
			int read = in.read(buf, offset, buf.length - offset);
			if(read == -1) break;
			offset += read;
		}
		if(offset == 0) return false;
		if(offset < buf.length) throw new EOFException(); // Unexpected EOF
		// Decrypt the block
		try {
			int i = frameCipher.update(buf, 0, offset, buf);
			if(i < offset) throw new RuntimeException();
		} catch(ShortBufferException badCipher) {
			throw new RuntimeException(badCipher);
		}
		return true;
	}

	private void initialiseCipher() {
		assert betweenFrames;
		if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		IvEncoder.updateIv(iv, frame);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		try {
			frameCipher.init(Cipher.DECRYPT_MODE, frameKey, ivSpec);
		} catch(InvalidAlgorithmParameterException badIv) {
			throw new RuntimeException(badIv);
		} catch(InvalidKeyException badKey) {
			throw new RuntimeException(badKey);
		}
		frame++;
		betweenFrames = false;
	}
}