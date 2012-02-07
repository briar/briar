package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import net.sf.briar.util.ByteUtils;

class HeaderEncoder {

	static void encodeHeader(byte[] header, long frameNumber, int payload,
			int padding, boolean lastFrame) {
		if(header.length < FRAME_HEADER_LENGTH)
			throw new IllegalArgumentException();
		if(frameNumber < 0 || frameNumber > ByteUtils.MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		if(payload < 0 || payload > ByteUtils.MAX_16_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		if(padding < 0 || padding > ByteUtils.MAX_16_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		ByteUtils.writeUint32(frameNumber, header, 0);
		ByteUtils.writeUint16(payload, header, 4);
		ByteUtils.writeUint16(padding, header, 6);
		if(lastFrame) header[8] = 1;
	}

	static boolean checkHeader(byte[] header, int length) {
		if(header.length < FRAME_HEADER_LENGTH)
			throw new IllegalArgumentException();
		int payload = getPayloadLength(header);
		int padding = getPaddingLength(header);
		if(FRAME_HEADER_LENGTH + payload + padding + MAC_LENGTH != length)
			return false;
		if(header[8] != 0 && header[8] != 1) return false;
		return true;
	}

	static long getFrameNumber(byte[] header) {
		if(header.length < FRAME_HEADER_LENGTH)
			throw new IllegalArgumentException();
		return ByteUtils.readUint32(header, 0);
	}

	static int getPayloadLength(byte[] header) {
		if(header.length < FRAME_HEADER_LENGTH)
			throw new IllegalArgumentException();
		return ByteUtils.readUint16(header, 4);
	}

	static int getPaddingLength(byte[] header) {
		if(header.length < FRAME_HEADER_LENGTH)
			throw new IllegalArgumentException();
		return ByteUtils.readUint16(header, 6);
	}

	static boolean isLastFrame(byte[] header) {
		if(header.length < FRAME_HEADER_LENGTH)
			throw new IllegalArgumentException();
		return header[8] == 1;
	}
}
