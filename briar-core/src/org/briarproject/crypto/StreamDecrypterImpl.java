package org.briarproject.crypto;

import org.briarproject.api.FormatException;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.StreamDecrypter;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.IV_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_PAYLOAD_LENGTH;

// FIXME: Implementation is incomplete, doesn't read the stream header
class StreamDecrypterImpl implements StreamDecrypter {

	private final InputStream in;
	private final AuthenticatedCipher frameCipher;
	private final SecretKey frameKey;
	private final byte[] iv, frameHeader, frameCiphertext;

	private long frameNumber;
	private boolean finalFrame;

	StreamDecrypterImpl(InputStream in, AuthenticatedCipher frameCipher,
			SecretKey headerKey) {
		this.in = in;
		this.frameCipher = frameCipher;
		this.frameKey = headerKey; // FIXME
		iv = new byte[IV_LENGTH];
		frameHeader = new byte[HEADER_LENGTH];
		frameCiphertext = new byte[MAX_FRAME_LENGTH];
		frameNumber = 0;
		finalFrame = false;
	}

	public int readFrame(byte[] payload) throws IOException {
		// The buffer must be big enough for a full-size frame
		if (payload.length < MAX_PAYLOAD_LENGTH)
			throw new IllegalArgumentException();
		if (finalFrame) return -1;
		// Read the frame header
		int offset = 0;
		while (offset < HEADER_LENGTH) {
			int read = in.read(frameCiphertext, offset, HEADER_LENGTH - offset);
			if (read == -1) throw new EOFException();
			offset += read;
		}
		// Decrypt and authenticate the frame header
		FrameEncoder.encodeIv(iv, frameNumber, true);
		try {
			frameCipher.init(false, frameKey, iv);
			int decrypted = frameCipher.process(frameCiphertext, 0,
					HEADER_LENGTH, frameHeader, 0);
			if (decrypted != HEADER_LENGTH - MAC_LENGTH)
				throw new RuntimeException();
		} catch (GeneralSecurityException e) {
			throw new FormatException();
		}
		// Decode and validate the frame header
		finalFrame = FrameEncoder.isFinalFrame(frameHeader);
		int payloadLength = FrameEncoder.getPayloadLength(frameHeader);
		int paddingLength = FrameEncoder.getPaddingLength(frameHeader);
		if (payloadLength + paddingLength > MAX_PAYLOAD_LENGTH)
			throw new FormatException();
		// Read the payload and padding
		int frameLength = HEADER_LENGTH + payloadLength + paddingLength
				+ MAC_LENGTH;
		while (offset < frameLength) {
			int read = in.read(frameCiphertext, offset, frameLength - offset);
			if (read == -1) throw new EOFException();
			offset += read;
		}
		// Decrypt and authenticate the payload and padding
		FrameEncoder.encodeIv(iv, frameNumber, false);
		try {
			frameCipher.init(false, frameKey, iv);
			int decrypted = frameCipher.process(frameCiphertext, HEADER_LENGTH,
					payloadLength + paddingLength + MAC_LENGTH, payload, 0);
			if (decrypted != payloadLength + paddingLength)
				throw new RuntimeException();
		} catch (GeneralSecurityException e) {
			throw new FormatException();
		}
		// If there's any padding it must be all zeroes
		for (int i = 0; i < paddingLength; i++)
			if (payload[payloadLength + i] != 0) throw new FormatException();
		frameNumber++;
		return payloadLength;
	}
}