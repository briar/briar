package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.ErasableKey;

class ConnectionDecrypter implements FrameSource {

	private final InputStream in;
	private final Cipher tagCipher, frameCipher;
	private final ErasableKey tagKey, frameKey;
	private final int macLength, blockSize;
	private final byte[] iv;
	private final boolean tagEverySegment;

	private long frame = 0L;

	ConnectionDecrypter(InputStream in, Cipher tagCipher, Cipher frameCipher,
			ErasableKey tagKey, ErasableKey frameKey, int macLength,
			boolean tagEverySegment) {
		this.in = in;
		this.tagCipher = tagCipher;
		this.frameCipher = frameCipher;
		this.tagKey = tagKey;
		this.frameKey = frameKey;
		this.macLength = macLength;
		this.tagEverySegment = tagEverySegment;
		blockSize = frameCipher.getBlockSize();
		if(blockSize < FRAME_HEADER_LENGTH)
			throw new IllegalArgumentException();
		iv = IvEncoder.encodeIv(0, blockSize);
	}

	public int readFrame(byte[] b) throws IOException {
		if(b.length < MAX_FRAME_LENGTH) throw new IllegalArgumentException();
		if(frame > MAX_32_BIT_UNSIGNED) throw new IllegalStateException();
		boolean tag = tagEverySegment && frame > 0;
		// Clear the buffer before exposing it to the transport plugin
		for(int i = 0; i < b.length; i++) b[i] = 0;
		try {
			// If a tag is expected then read, decrypt and validate it
			if(tag) {
				int offset = 0;
				while(offset < TAG_LENGTH) {
					int read = in.read(b, offset, TAG_LENGTH - offset);
					if(read == -1) {
						if(offset == 0) return -1;
						throw new EOFException();
					}
					offset += read;
				}
				if(!TagEncoder.validateTag(b, frame, tagCipher, tagKey))
					throw new FormatException();
			}
			// Read the first block
			int offset = 0;
			while(offset < blockSize) {
				int read = in.read(b, offset, blockSize - offset);
				if(read == -1) {
					if(offset == 0 && !tag) return -1;
					throw new EOFException();
				}
				offset += read;
			}
			// Decrypt the first block
			try {
				IvEncoder.updateIv(iv, frame);
				IvParameterSpec ivSpec = new IvParameterSpec(iv);
				frameCipher.init(Cipher.DECRYPT_MODE, frameKey, ivSpec);
				int decrypted = frameCipher.update(b, 0, blockSize, b);
				if(decrypted != blockSize) throw new RuntimeException();
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
				if(decrypted != length - blockSize)
					throw new RuntimeException();
			} catch(GeneralSecurityException badCipher) {
				throw new RuntimeException(badCipher);
			}
			frame++;
			return length;
		} catch(IOException e) {
			frameKey.erase();
			tagKey.erase();
			throw e;
		}
	}
}