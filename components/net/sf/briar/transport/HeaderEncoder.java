package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import net.sf.briar.util.ByteUtils;

class HeaderEncoder {

	static void encodeHeader(byte[] header, long frameNumber, int payload,
			int padding) {
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
	}

	static boolean validateHeader(byte[] header) {
		if(header.length < FRAME_HEADER_LENGTH) return false;
		int payload = ByteUtils.readUint16(header, 4);
		int padding = ByteUtils.readUint16(header, 6);
		int frameLength = FRAME_HEADER_LENGTH + payload + padding + MAC_LENGTH;
		if(frameLength > MAX_FRAME_LENGTH) return false;
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
}
