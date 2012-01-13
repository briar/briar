package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.plugins.FrameSource;

class SegmentedConnectionDecrypter implements FrameSource {

	private final FrameSource in;
	private final Cipher frameCipher;
	private final ErasableKey frameKey;
	private final int macLength, blockSize;
	private final byte[] iv;

	private long frame = 0L;

	SegmentedConnectionDecrypter(FrameSource in, Cipher frameCipher,
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
			// Read the frame
			int length = in.readFrame(b);
			if(length == -1) return -1;
			if(length > MAX_FRAME_LENGTH) throw new FormatException();
			if(length < FRAME_HEADER_LENGTH + macLength)
				throw new FormatException();
			// Decrypt the frame
			try {
				int decrypted = frameCipher.update(b, 0, length, b);
				assert decrypted == length;
			} catch(GeneralSecurityException badCipher) {
				throw new RuntimeException(badCipher);
			}
			// Validate and parse the header
			int max = MAX_FRAME_LENGTH - FRAME_HEADER_LENGTH - macLength;
			if(!HeaderEncoder.validateHeader(b, frame, max))
				throw new FormatException();
			int payload = HeaderEncoder.getPayloadLength(b);
			int padding = HeaderEncoder.getPaddingLength(b);
			if(length != FRAME_HEADER_LENGTH + payload + padding + macLength)
				throw new FormatException();
			frame++;
			return length;
		} catch(IOException e) {
			frameKey.erase();
			throw e;
		}
	}
}
