package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.ErasableKey;

class ConnectionDecrypterImpl implements ConnectionDecrypter {

	private final InputStream in;
	private final Cipher frameCipher;
	private final ErasableKey frameKey;
	private final int macLength, blockSize;
	private final byte[] iv;

	private long frame = 0L;

	ConnectionDecrypterImpl(InputStream in, Cipher frameCipher,
			ErasableKey frameKey, int macLength) {
		this.in = in;
		this.frameCipher = frameCipher;
		this.frameKey = frameKey;
		this.macLength = macLength;
		blockSize = frameCipher.getBlockSize();
		if(blockSize < FRAME_HEADER_LENGTH)
			throw new IllegalArgumentException();
		iv = IvEncoder.encodeIv(0, blockSize);
	}

	public int readFrame(byte[] b) throws IOException {
		if(b.length < MAX_FRAME_LENGTH) throw new IllegalArgumentException();
		if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		// Initialise the cipher
		IvEncoder.updateIv(iv, frame);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		try {
			frameCipher.init(Cipher.DECRYPT_MODE, frameKey, ivSpec);
		} catch(GeneralSecurityException badIvOrKey) {
			throw new RuntimeException(badIvOrKey);
		}
		try {
			// Read the first block
			int offset = 0;
			while(offset < blockSize) {
				int read = in.read(b, offset, blockSize - offset);
				if(read == -1) {
					if(offset == 0) return -1;
					if(offset < blockSize) throw new EOFException();
					break;
				}
				offset += read;
			}
			// Decrypt the first block
			try {
				int decrypted = frameCipher.update(b, 0, blockSize, b);
				assert decrypted == blockSize;
			} catch(GeneralSecurityException badCipher) {
				throw new RuntimeException(badCipher);
			}
			// Validate and parse the header
			int max = MAX_FRAME_LENGTH - FRAME_HEADER_LENGTH - macLength;
			if(!HeaderEncoder.validateHeader(b, frame, max))
				throw new FormatException();
			int payload = HeaderEncoder.getPayloadLength(b);
			int padding = HeaderEncoder.getPaddingLength(b);
			int length = FRAME_HEADER_LENGTH + payload + padding + macLength;
			if(length > MAX_FRAME_LENGTH) throw new FormatException();
			// Read the remainder of the frame
			while(offset < length) {
				int read = in.read(b, offset, length - offset);
				if(read == -1) throw new EOFException();
				offset += read;
			}
			// Decrypt the remainder of the frame
			try {
				int decrypted = frameCipher.doFinal(b, blockSize,
						length - blockSize, b, blockSize);
				assert decrypted == length - blockSize;
			} catch(GeneralSecurityException badCipher) {
				throw new RuntimeException(badCipher);
			}
			frame++;
			return length;
		} catch(IOException e) {
			frameKey.erase();
			throw e;
		}
	}
}