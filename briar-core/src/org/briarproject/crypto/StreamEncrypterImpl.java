package org.briarproject.crypto;

import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.StreamEncrypter;

import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.IV_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_PAYLOAD_LENGTH;
import static org.briarproject.util.ByteUtils.MAX_32_BIT_UNSIGNED;

// FIXME: Implementation is incomplete, doesn't write the stream header
class StreamEncrypterImpl implements StreamEncrypter {

	private final OutputStream out;
	private final AuthenticatedCipher frameCipher;
	private final SecretKey frameKey;
	private final byte[] tag, iv, framePlaintext, frameCiphertext;

	private long frameNumber;
	private boolean writeTag;

	StreamEncrypterImpl(OutputStream out, AuthenticatedCipher frameCipher,
			SecretKey headerKey, byte[] tag) {
		this.out = out;
		this.frameCipher = frameCipher;
		this.frameKey = headerKey; // FIXME
		this.tag = tag;
		iv = new byte[IV_LENGTH];
		framePlaintext = new byte[HEADER_LENGTH + MAX_PAYLOAD_LENGTH];
		frameCiphertext = new byte[MAX_FRAME_LENGTH];
		frameNumber = 0;
		writeTag = (tag != null);
	}

	public void writeFrame(byte[] payload, int payloadLength,
			int paddingLength, boolean finalFrame) throws IOException {
		if (payloadLength + paddingLength > MAX_PAYLOAD_LENGTH)
			throw new IllegalArgumentException();
		// Don't allow the frame counter to wrap
		if (frameNumber > MAX_32_BIT_UNSIGNED) throw new IOException();
		// Write the tag if required
		if (writeTag) {
			out.write(tag, 0, tag.length);
			writeTag = false;
		}
		// Encode the frame header
		FrameEncoder.encodeHeader(framePlaintext, finalFrame, payloadLength,
				paddingLength);
		// Encrypt and authenticate the frame header
		FrameEncoder.encodeIv(iv, frameNumber, true);
		try {
			frameCipher.init(true, frameKey, iv);
			int encrypted = frameCipher.process(framePlaintext, 0,
					HEADER_LENGTH - MAC_LENGTH, frameCiphertext, 0);
			if (encrypted != HEADER_LENGTH) throw new RuntimeException();
		} catch (GeneralSecurityException badCipher) {
			throw new RuntimeException(badCipher);
		}
		// Combine the payload and padding
		System.arraycopy(payload, 0, framePlaintext, HEADER_LENGTH,
				payloadLength);
		for (int i = 0; i < paddingLength; i++)
			framePlaintext[HEADER_LENGTH + payloadLength + i] = 0;
		// Encrypt and authenticate the payload and padding
		FrameEncoder.encodeIv(iv, frameNumber, false);
		try {
			frameCipher.init(true, frameKey, iv);
			int encrypted = frameCipher.process(framePlaintext, HEADER_LENGTH,
					payloadLength + paddingLength, frameCiphertext,
					HEADER_LENGTH);
			if (encrypted != payloadLength + paddingLength + MAC_LENGTH)
				throw new RuntimeException();
		} catch (GeneralSecurityException badCipher) {
			throw new RuntimeException(badCipher);
		}
		// Write the frame
		out.write(frameCiphertext, 0, HEADER_LENGTH + payloadLength
				+ paddingLength + MAC_LENGTH);
		frameNumber++;
	}

	public void flush() throws IOException {
		// Write the tag if required
		if (writeTag) {
			out.write(tag, 0, tag.length);
			writeTag = false;
		}
		out.flush();
	}
}