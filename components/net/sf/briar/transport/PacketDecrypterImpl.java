package net.sf.briar.transport;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;

class PacketDecrypterImpl extends FilterInputStream implements PacketDecrypter {

	private final Cipher tagCipher, packetCipher;
	private final SecretKey packetKey;

	private byte[] cipherBuf, plainBuf;
	private int bufOff = 0, bufLen = Constants.TAG_BYTES;
	private boolean betweenPackets = true;

	PacketDecrypterImpl(byte[] firstTag, InputStream in, Cipher tagCipher,
			Cipher packetCipher, SecretKey tagKey, SecretKey packetKey) {
		super(in);
		if(firstTag.length != Constants.TAG_BYTES)
			throw new IllegalArgumentException();
		cipherBuf = Arrays.copyOf(firstTag, firstTag.length);
		plainBuf = new byte[Constants.TAG_BYTES];
		this.tagCipher = tagCipher;
		this.packetCipher = packetCipher;
		this.packetKey = packetKey;
		try {
			tagCipher.init(Cipher.DECRYPT_MODE, tagKey);
		} catch(InvalidKeyException e) {
			throw new IllegalArgumentException(e);
		}
		if(tagCipher.getOutputSize(Constants.TAG_BYTES) != Constants.TAG_BYTES)
			throw new IllegalArgumentException();
	}

	public InputStream getInputStream() {
		return this;
	}

	public byte[] readTag() throws IOException {
		byte[] tag = new byte[Constants.TAG_BYTES];
		System.arraycopy(cipherBuf, bufOff, tag, 0, bufLen);
		int offset = bufLen;
		bufOff = bufLen = 0;
		while(offset < tag.length) {
			int read = in.read(tag, offset, tag.length - offset);
			if(read == -1) throw new EOFException();
			offset += read;
		}
		betweenPackets = false;
		try {
			byte[] decryptedTag = tagCipher.doFinal(tag);
			IvParameterSpec iv = new IvParameterSpec(decryptedTag);
			packetCipher.init(Cipher.DECRYPT_MODE, packetKey, iv);
			return decryptedTag;
		} catch(BadPaddingException badCipher) {
			throw new RuntimeException(badCipher);
		} catch(IllegalBlockSizeException badCipher) {
			throw new RuntimeException(badCipher);
		} catch(InvalidAlgorithmParameterException badIv) {
			throw new RuntimeException(badIv);
		} catch(InvalidKeyException badKey) {
			throw new RuntimeException(badKey);
		}
	}

	@Override
	public int read() throws IOException {
		if(betweenPackets) throw new IllegalStateException();
		if(bufLen == 0) {
			int read = readBlock();
			if(read == 0) return -1;
			bufOff = 0;
			bufLen = read;
		}
		int i = plainBuf[bufOff];
		bufOff++;
		bufLen--;
		return i < 0 ? i + 256 : i;
	}

	// Although we're using CTR mode, which doesn't require full blocks of
	// ciphertext, the cipher still tries to operate a block at a time. We must
	// either call update() with a full block or doFinal() with the last
	// (possibly partial) block.
	private int readBlock() throws IOException {
		// Try to read a block of ciphertext
		int off = 0;
		while(off < cipherBuf.length) {
			int read = in.read(cipherBuf, off, cipherBuf.length - off);
			if(read == -1) break;
			off += read;
		}
		if(off == 0) return 0;
		// Did we get a whole block? If not we must be at EOF
		if(off < cipherBuf.length) {
			// We're at EOF so we can call doFinal() to force decryption
			try {
				int i = packetCipher.doFinal(cipherBuf, 0, off, plainBuf);
				if(i < off) throw new RuntimeException();
				betweenPackets = true;
			} catch(BadPaddingException badCipher) {
				throw new RuntimeException(badCipher);
			} catch(IllegalBlockSizeException badCipher) {
				throw new RuntimeException(badCipher);
			} catch(ShortBufferException badCipher) {
				throw new RuntimeException(badCipher);
			}
		} else {
			// We're not at EOF but we have a whole block to decrypt
			try {
				int i = packetCipher.update(cipherBuf, 0, off, plainBuf);
				if(i < off) throw new RuntimeException();
			} catch(ShortBufferException badCipher) {
				throw new RuntimeException(badCipher);
			}
		}
		return off;
	}

	@Override
	public int read(byte[] b) throws IOException {
		if(betweenPackets) throw new IllegalStateException();
		if(bufLen == 0) {
			int read = readBlock();
			if(read == 0) return -1;
			bufOff = 0;
			bufLen = read;
		}
		int length = Math.min(b.length, bufLen);
		System.arraycopy(plainBuf, bufOff, b, 0, length);
		bufOff += length;
		bufLen -= length;
		return length;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if(betweenPackets) throw new IllegalStateException();
		if(bufLen == 0) {
			int read = readBlock();
			if(read == 0) return -1;
			bufOff = 0;
			bufLen = read;
		}
		int length = Math.min(len, bufLen);
		System.arraycopy(plainBuf, bufOff, b, off, length);
		bufOff += length;
		bufLen -= length;
		return length;
	}
}
