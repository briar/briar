package org.briarproject.transport;

import static org.briarproject.api.transport.TransportConstants.AAD_LENGTH;
import static org.briarproject.api.transport.TransportConstants.HEADER_LENGTH;
import static org.briarproject.api.transport.TransportConstants.IV_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAC_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.briarproject.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import org.briarproject.util.ByteUtils;

class FrameEncoder {

	static void encodeIv(byte[] iv, long frameNumber) {
		if(iv.length < IV_LENGTH) throw new IllegalArgumentException();
		if(frameNumber < 0 || frameNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		ByteUtils.writeUint32(frameNumber, iv, 0);
		for(int i = 4; i < IV_LENGTH; i++) iv[i] = 0;
	}

	static void encodeAad(byte[] aad, long frameNumber, int plaintextLength) {
		if(aad.length < AAD_LENGTH) throw new IllegalArgumentException();
		if(frameNumber < 0 || frameNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		if(plaintextLength < HEADER_LENGTH)
			throw new IllegalArgumentException();
		if(plaintextLength > MAX_FRAME_LENGTH - MAC_LENGTH)
			throw new IllegalArgumentException();
		ByteUtils.writeUint32(frameNumber, aad, 0);
		ByteUtils.writeUint16(plaintextLength, aad, 4);
	}

	static void encodeHeader(byte[] header, boolean finalFrame,
			int payloadLength) {
		if(header.length < HEADER_LENGTH) throw new IllegalArgumentException();
		if(payloadLength < 0)
			throw new IllegalArgumentException();
		if(payloadLength > MAX_FRAME_LENGTH - HEADER_LENGTH - MAC_LENGTH)
			throw new IllegalArgumentException();
		ByteUtils.writeUint16(payloadLength, header, 0);
		if(finalFrame) header[0] |= 0x80;
	}

	static boolean isFinalFrame(byte[] header) {
		if(header.length < HEADER_LENGTH) throw new IllegalArgumentException();
		return (header[0] & 0x80) == 0x80;
	}

	static int getPayloadLength(byte[] header) {
		if(header.length < HEADER_LENGTH) throw new IllegalArgumentException();
		return ByteUtils.readUint16(header, 0) & 0x7FFF;
	}
}
