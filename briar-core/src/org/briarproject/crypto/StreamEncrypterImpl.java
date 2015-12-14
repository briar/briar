package org.briarproject.crypto;

import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.StreamEncrypter;

import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

import static org.briarproject.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.FRAME_IV_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_PAYLOAD_LENGTH;
import static org.briarproject.api.transport.TransportConstants.STREAM_HEADER_IV_LENGTH;
import static org.briarproject.api.transport.TransportConstants.STREAM_HEADER_LENGTH;
import static org.briarproject.util.ByteUtils.MAX_32_BIT_UNSIGNED;

class StreamEncrypterImpl implements StreamEncrypter {

	private final OutputStream out;
	private final AuthenticatedCipher cipher;
	private final SecretKey streamHeaderKey, frameKey;
	private final byte[] tag, streamHeaderIv;
	private final byte[] frameIv, framePlaintext, frameCiphertext;

	private long frameNumber;
	private boolean writeTag, writeStreamHeader;

	StreamEncrypterImpl(OutputStream out, AuthenticatedCipher cipher,
			byte[] tag, byte[] streamHeaderIv, SecretKey streamHeaderKey,
			SecretKey frameKey) {
		this.out = out;
		this.cipher = cipher;
		this.tag = tag;
		this.streamHeaderIv = streamHeaderIv;
		this.streamHeaderKey = streamHeaderKey;
		this.frameKey = frameKey;
		frameIv = new byte[FRAME_IV_LENGTH];
		framePlaintext = new byte[FRAME_HEADER_LENGTH + MAX_PAYLOAD_LENGTH];
		frameCiphertext = new byte[MAX_FRAME_LENGTH];
		frameNumber = 0;
		writeTag = (tag != null);
		writeStreamHeader = true;
	}

	public void writeFrame(byte[] payload, int payloadLength,
			int paddingLength, boolean finalFrame) throws IOException {
		if (payloadLength + paddingLength > MAX_PAYLOAD_LENGTH)
			throw new IllegalArgumentException();
		// Don't allow the frame counter to wrap
		if (frameNumber > MAX_32_BIT_UNSIGNED) throw new IOException();
		// Write the tag if required
		if (writeTag) writeTag();
		// Write the stream header if required
		if (writeStreamHeader) writeStreamHeader();
		// Encode the frame header
		FrameEncoder.encodeHeader(framePlaintext, finalFrame, payloadLength,
				paddingLength);
		// Encrypt and authenticate the frame header
		FrameEncoder.encodeIv(frameIv, frameNumber, true);
		try {
			cipher.init(true, frameKey, frameIv);
			int encrypted = cipher.process(framePlaintext, 0,
					FRAME_HEADER_LENGTH - MAC_LENGTH, frameCiphertext, 0);
			if (encrypted != FRAME_HEADER_LENGTH) throw new RuntimeException();
		} catch (GeneralSecurityException badCipher) {
			throw new RuntimeException(badCipher);
		}
		// Combine the payload and padding
		System.arraycopy(payload, 0, framePlaintext, FRAME_HEADER_LENGTH,
				payloadLength);
		for (int i = 0; i < paddingLength; i++)
			framePlaintext[FRAME_HEADER_LENGTH + payloadLength + i] = 0;
		// Encrypt and authenticate the payload and padding
		FrameEncoder.encodeIv(frameIv, frameNumber, false);
		try {
			cipher.init(true, frameKey, frameIv);
			int encrypted = cipher.process(framePlaintext, FRAME_HEADER_LENGTH,
					payloadLength + paddingLength, frameCiphertext,
					FRAME_HEADER_LENGTH);
			if (encrypted != payloadLength + paddingLength + MAC_LENGTH)
				throw new RuntimeException();
		} catch (GeneralSecurityException badCipher) {
			throw new RuntimeException(badCipher);
		}
		// Write the frame
		out.write(frameCiphertext, 0, FRAME_HEADER_LENGTH + payloadLength
				+ paddingLength + MAC_LENGTH);
		frameNumber++;
	}

	private void writeTag() throws IOException {
		out.write(tag, 0, tag.length);
		writeTag = false;
	}

	private void writeStreamHeader() throws IOException {
		byte[] streamHeaderPlaintext = frameKey.getBytes();
		byte[] streamHeaderCiphertext = new byte[STREAM_HEADER_LENGTH];
		System.arraycopy(streamHeaderIv, 0, streamHeaderCiphertext, 0,
				STREAM_HEADER_IV_LENGTH);
		// Encrypt and authenticate the frame key
		try {
			cipher.init(true, streamHeaderKey, streamHeaderIv);
			int encrypted = cipher.process(streamHeaderPlaintext, 0,
					SecretKey.LENGTH, streamHeaderCiphertext,
					STREAM_HEADER_IV_LENGTH);
			if (encrypted != SecretKey.LENGTH + MAC_LENGTH)
				throw new RuntimeException();
		} catch (GeneralSecurityException badCipher) {
			throw new RuntimeException(badCipher);
		}
		out.write(streamHeaderCiphertext);
		writeStreamHeader = false;
	}

	public void flush() throws IOException {
		// Write the tag if required
		if (writeTag) writeTag();
		// Write the stream header if required
		if (writeStreamHeader) writeStreamHeader();
		out.flush();
	}
}