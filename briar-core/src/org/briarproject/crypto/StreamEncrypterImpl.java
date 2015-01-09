package org.briarproject.crypto;

import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.IV_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_PAYLOAD_LENGTH;
import static org.briarproject.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.StreamEncrypter;

class StreamEncrypterImpl implements StreamEncrypter {

	private final OutputStream out;
	private final AuthenticatedCipher frameCipher;
	private final SecretKey frameKey;
	private final byte[] tag, iv, plaintext, ciphertext;

	private long frameNumber;
	private boolean writeTag;

	StreamEncrypterImpl(OutputStream out, AuthenticatedCipher frameCipher,
			SecretKey frameKey, byte[] tag) {
		this.out = out;
		this.frameCipher = frameCipher;
		this.frameKey = frameKey;
		this.tag = tag;
		iv = new byte[IV_LENGTH];
		plaintext = new byte[HEADER_LENGTH + MAX_PAYLOAD_LENGTH];
		ciphertext = new byte[MAX_FRAME_LENGTH];
		frameNumber = 0;
		writeTag = (tag != null);
	}

	public void writeFrame(byte[] payload, int payloadLength,
			int paddingLength, boolean finalFrame) throws IOException {
		if(payloadLength + paddingLength > MAX_PAYLOAD_LENGTH)
			throw new IllegalArgumentException();
		// Don't allow the frame counter to wrap
		if(frameNumber > MAX_32_BIT_UNSIGNED) throw new IOException();
		// Write the tag if required
		if(writeTag) {
			out.write(tag, 0, tag.length);
			writeTag = false;
		}
		// Encode the header
		FrameEncoder.encodeHeader(plaintext, finalFrame, payloadLength,
				paddingLength);
		// Encrypt and authenticate the header
		FrameEncoder.encodeIv(iv, frameNumber, true);
		try {
			frameCipher.init(true, frameKey, iv);
			int encrypted = frameCipher.process(plaintext, 0,
					HEADER_LENGTH - MAC_LENGTH, ciphertext, 0);
			if(encrypted != HEADER_LENGTH) throw new RuntimeException();
		} catch(GeneralSecurityException badCipher) {
			throw new RuntimeException(badCipher);
		}
		// Combine the payload and padding
		System.arraycopy(payload, 0, plaintext, HEADER_LENGTH, payloadLength);
		for(int i = 0; i < paddingLength; i++)
			plaintext[HEADER_LENGTH + payloadLength + i] = 0;
		// Encrypt and authenticate the payload and padding
		FrameEncoder.encodeIv(iv, frameNumber, false);
		try {
			frameCipher.init(true, frameKey, iv);
			int encrypted = frameCipher.process(plaintext, HEADER_LENGTH,
					payloadLength + paddingLength, ciphertext, HEADER_LENGTH);
			if(encrypted != payloadLength + paddingLength + MAC_LENGTH)
				throw new RuntimeException();
		} catch(GeneralSecurityException badCipher) {
			throw new RuntimeException(badCipher);
		}
		// Write the frame
		out.write(ciphertext, 0, HEADER_LENGTH + payloadLength + paddingLength
				+ MAC_LENGTH);
		frameNumber++;
	}

	public void flush() throws IOException {
		// Write the tag if required
		if(writeTag) {
			out.write(tag, 0, tag.length);
			writeTag = false;
		}
		out.flush();
	}
}