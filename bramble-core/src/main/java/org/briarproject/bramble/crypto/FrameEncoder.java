package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.ByteUtils;

import static org.briarproject.bramble.api.transport.TransportConstants.FRAME_HEADER_PLAINTEXT_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.FRAME_NONCE_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.MAX_PAYLOAD_LENGTH;
import static org.briarproject.bramble.util.ByteUtils.INT_16_BYTES;
import static org.briarproject.bramble.util.ByteUtils.INT_64_BYTES;

@NotNullByDefault
class FrameEncoder {

	static void encodeNonce(byte[] dest, long frameNumber, boolean header) {
		if (dest.length < FRAME_NONCE_LENGTH)
			throw new IllegalArgumentException();
		if (frameNumber < 0) throw new IllegalArgumentException();
		ByteUtils.writeUint64(frameNumber, dest, 0);
		if (header) dest[0] |= 0x80;
		for (int i = INT_64_BYTES; i < FRAME_NONCE_LENGTH; i++) dest[i] = 0;
	}

	static void encodeHeader(byte[] dest, boolean finalFrame,
			int payloadLength, int paddingLength) {
		if (dest.length < FRAME_HEADER_PLAINTEXT_LENGTH)
			throw new IllegalArgumentException();
		if (payloadLength < 0) throw new IllegalArgumentException();
		if (paddingLength < 0) throw new IllegalArgumentException();
		if (payloadLength + paddingLength > MAX_PAYLOAD_LENGTH)
			throw new IllegalArgumentException();
		ByteUtils.writeUint16(payloadLength, dest, 0);
		ByteUtils.writeUint16(paddingLength, dest, INT_16_BYTES);
		if (finalFrame) dest[0] |= 0x80;
	}

	static boolean isFinalFrame(byte[] header) {
		if (header.length < FRAME_HEADER_PLAINTEXT_LENGTH)
			throw new IllegalArgumentException();
		return (header[0] & 0x80) == 0x80;
	}

	static int getPayloadLength(byte[] header) {
		if (header.length < FRAME_HEADER_PLAINTEXT_LENGTH)
			throw new IllegalArgumentException();
		return ByteUtils.readUint16(header, 0) & 0x7FFF;
	}

	static int getPaddingLength(byte[] header) {
		if (header.length < FRAME_HEADER_PLAINTEXT_LENGTH)
			throw new IllegalArgumentException();
		return ByteUtils.readUint16(header, INT_16_BYTES);
	}
}
