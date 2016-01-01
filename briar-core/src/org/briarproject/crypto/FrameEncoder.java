package org.briarproject.crypto;

import org.briarproject.util.ByteUtils;

import static org.briarproject.api.transport.TransportConstants.FRAME_HEADER_PAYLOAD_LENGTH;
import static org.briarproject.api.transport.TransportConstants.FRAME_IV_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_PAYLOAD_LENGTH;

class FrameEncoder {

	static void encodeIv(byte[] iv, long frameNumber, boolean header) {
		if (iv.length < FRAME_IV_LENGTH) throw new IllegalArgumentException();
		if (frameNumber < 0) throw new IllegalArgumentException();
		ByteUtils.writeUint64(frameNumber, iv, 0);
		if (header) iv[0] |= 0x80;
		for (int i = 8; i < FRAME_IV_LENGTH; i++) iv[i] = 0;
	}

	static void encodeHeader(byte[] header, boolean finalFrame,
			int payloadLength, int paddingLength) {
		if (header.length < FRAME_HEADER_PAYLOAD_LENGTH)
			throw new IllegalArgumentException();
		if (payloadLength < 0) throw new IllegalArgumentException();
		if (paddingLength < 0) throw new IllegalArgumentException();
		if (payloadLength + paddingLength > MAX_PAYLOAD_LENGTH)
			throw new IllegalArgumentException();
		ByteUtils.writeUint16(payloadLength, header, 0);
		ByteUtils.writeUint16(paddingLength, header, 2);
		if (finalFrame) header[0] |= 0x80;
	}

	static boolean isFinalFrame(byte[] header) {
		if (header.length < FRAME_HEADER_PAYLOAD_LENGTH)
			throw new IllegalArgumentException();
		return (header[0] & 0x80) == 0x80;
	}

	static int getPayloadLength(byte[] header) {
		if (header.length < FRAME_HEADER_PAYLOAD_LENGTH)
			throw new IllegalArgumentException();
		return ByteUtils.readUint16(header, 0) & 0x7FFF;
	}

	static int getPaddingLength(byte[] header) {
		if (header.length < FRAME_HEADER_PAYLOAD_LENGTH)
			throw new IllegalArgumentException();
		return ByteUtils.readUint16(header, 2);
	}
}
