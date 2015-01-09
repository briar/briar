package org.briarproject.crypto;

import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.IV_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_PAYLOAD_LENGTH;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import org.briarproject.api.FormatException;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.StreamDecrypter;

class StreamDecrypterImpl implements StreamDecrypter {

	private final InputStream in;
	private final AuthenticatedCipher frameCipher;
	private final SecretKey frameKey;
	private final byte[] iv, header, ciphertext;

	private long frameNumber;
	private boolean finalFrame;

	StreamDecrypterImpl(InputStream in, AuthenticatedCipher frameCipher,
			SecretKey frameKey) {
		this.in = in;
		this.frameCipher = frameCipher;
		this.frameKey = frameKey;
		iv = new byte[IV_LENGTH];
		header = new byte[HEADER_LENGTH];
		ciphertext = new byte[MAX_FRAME_LENGTH];
		frameNumber = 0;
		finalFrame = false;
	}

	public int readFrame(byte[] payload) throws IOException {
		if(payload.length < MAX_PAYLOAD_LENGTH)
			throw new IllegalArgumentException();
		if(finalFrame) return -1;
		// Read the header
		int offset = 0;
		while(offset < HEADER_LENGTH) {
			int read = in.read(ciphertext, offset, HEADER_LENGTH - offset);
			if(read == -1) throw new EOFException();
			offset += read;
		}
		// Decrypt and authenticate the header
		FrameEncoder.encodeIv(iv, frameNumber, true);
		try {
			frameCipher.init(false, frameKey, iv);
			int decrypted = frameCipher.process(ciphertext, 0, HEADER_LENGTH,
					header, 0);
			if(decrypted != HEADER_LENGTH - MAC_LENGTH)
				throw new RuntimeException();
		} catch(GeneralSecurityException e) {
			throw new FormatException();
		}
		// Decode and validate the header
		finalFrame = FrameEncoder.isFinalFrame(header);
		int payloadLength = FrameEncoder.getPayloadLength(header);
		int paddingLength = FrameEncoder.getPaddingLength(header);
		if(payloadLength + paddingLength > MAX_PAYLOAD_LENGTH)
			throw new FormatException();
		// Read the payload and padding
		int frameLength = HEADER_LENGTH + payloadLength + paddingLength
				+ MAC_LENGTH;
		while(offset < frameLength) {
			int read = in.read(ciphertext, offset, frameLength - offset);
			if(read == -1) throw new EOFException();
			offset += read;
		}
		// Decrypt and authenticate the payload and padding
		FrameEncoder.encodeIv(iv, frameNumber, false);
		try {
			frameCipher.init(false, frameKey, iv);
			int decrypted = frameCipher.process(ciphertext, HEADER_LENGTH,
					payloadLength + paddingLength + MAC_LENGTH, payload, 0);
			if(decrypted != payloadLength + paddingLength)
				throw new RuntimeException();
		} catch(GeneralSecurityException e) {
			throw new FormatException();
		}
		// If there's any padding it must be all zeroes
		for(int i = 0; i < paddingLength; i++)
			if(payload[payloadLength + i] != 0) throw new FormatException();
		frameNumber++;
		return payloadLength;
	}
}