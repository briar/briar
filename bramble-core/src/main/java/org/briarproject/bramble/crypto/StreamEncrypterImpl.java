package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.crypto.StreamEncrypter;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.ByteUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import static org.briarproject.bramble.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.FRAME_HEADER_PLAINTEXT_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.FRAME_NONCE_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.MAX_PAYLOAD_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.STREAM_HEADER_IV_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.STREAM_HEADER_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.STREAM_HEADER_NONCE_LENGTH;
import static org.briarproject.bramble.util.ByteUtils.INT_64_BYTES;

@NotThreadSafe
@NotNullByDefault
class StreamEncrypterImpl implements StreamEncrypter {

	private final OutputStream out;
	private final AuthenticatedCipher cipher;
	private final SecretKey streamHeaderKey, frameKey;
	private final long streamNumber;
	@Nullable
	private final byte[] tag;
	private final byte[] streamHeaderIv;
	private final byte[] frameNonce, frameHeader;
	private final byte[] framePlaintext, frameCiphertext;

	private long frameNumber;
	private boolean writeTag, writeStreamHeader;

	StreamEncrypterImpl(OutputStream out, AuthenticatedCipher cipher,
			long streamNumber, @Nullable byte[] tag, byte[] streamHeaderIv,
			SecretKey streamHeaderKey, SecretKey frameKey) {
		this.out = out;
		this.cipher = cipher;
		this.streamNumber = streamNumber;
		this.tag = tag;
		this.streamHeaderIv = streamHeaderIv;
		this.streamHeaderKey = streamHeaderKey;
		this.frameKey = frameKey;
		frameNonce = new byte[FRAME_NONCE_LENGTH];
		frameHeader = new byte[FRAME_HEADER_PLAINTEXT_LENGTH];
		framePlaintext = new byte[MAX_PAYLOAD_LENGTH];
		frameCiphertext = new byte[MAX_FRAME_LENGTH];
		frameNumber = 0;
		writeTag = (tag != null);
		writeStreamHeader = true;
	}

	@Override
	public void writeFrame(byte[] payload, int payloadLength,
			int paddingLength, boolean finalFrame) throws IOException {
		if (payloadLength < 0 || paddingLength < 0)
			throw new IllegalArgumentException();
		if (payloadLength + paddingLength > MAX_PAYLOAD_LENGTH)
			throw new IllegalArgumentException();
		// Don't allow the frame counter to wrap
		if (frameNumber < 0) throw new IOException();
		// Write the tag if required
		if (writeTag) writeTag();
		// Write the stream header if required
		if (writeStreamHeader) writeStreamHeader();
		// Encode the frame header
		FrameEncoder.encodeHeader(frameHeader, finalFrame, payloadLength,
				paddingLength);
		// Encrypt and authenticate the frame header
		FrameEncoder.encodeNonce(frameNonce, frameNumber, true);
		try {
			cipher.init(true, frameKey, frameNonce);
			int encrypted = cipher.process(frameHeader, 0,
					FRAME_HEADER_PLAINTEXT_LENGTH, frameCiphertext, 0);
			if (encrypted != FRAME_HEADER_LENGTH) throw new RuntimeException();
		} catch (GeneralSecurityException badCipher) {
			throw new RuntimeException(badCipher);
		}
		// Combine the payload and padding
		System.arraycopy(payload, 0, framePlaintext, 0, payloadLength);
		for (int i = 0; i < paddingLength; i++)
			framePlaintext[payloadLength + i] = 0;
		// Encrypt and authenticate the payload and padding
		FrameEncoder.encodeNonce(frameNonce, frameNumber, false);
		try {
			cipher.init(true, frameKey, frameNonce);
			int encrypted = cipher.process(framePlaintext, 0,
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
		if (tag == null) throw new IllegalStateException();
		out.write(tag, 0, tag.length);
		writeTag = false;
	}

	private void writeStreamHeader() throws IOException {
		// The nonce consists of the stream number followed by the IV
		byte[] streamHeaderNonce = new byte[STREAM_HEADER_NONCE_LENGTH];
		ByteUtils.writeUint64(streamNumber, streamHeaderNonce, 0);
		System.arraycopy(streamHeaderIv, 0, streamHeaderNonce, INT_64_BYTES,
				STREAM_HEADER_IV_LENGTH);
		byte[] streamHeaderPlaintext = frameKey.getBytes();
		byte[] streamHeaderCiphertext = new byte[STREAM_HEADER_LENGTH];
		System.arraycopy(streamHeaderIv, 0, streamHeaderCiphertext, 0,
				STREAM_HEADER_IV_LENGTH);
		// Encrypt and authenticate the frame key
		try {
			cipher.init(true, streamHeaderKey, streamHeaderNonce);
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

	@Override
	public void flush() throws IOException {
		// Write the tag if required
		if (writeTag) writeTag();
		// Write the stream header if required
		if (writeStreamHeader) writeStreamHeader();
		out.flush();
	}
}