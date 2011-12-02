package net.sf.briar.transport;

import net.sf.briar.api.transport.TransportConstants;
import net.sf.briar.util.ByteUtils;

class HeaderEncoder {

	static void encodeHeader(byte[] header, long frame, int payload,
			int padding) {
		if(header.length < TransportConstants.FRAME_HEADER_LENGTH)
			throw new IllegalArgumentException();
		if(frame < 0 || frame > ByteUtils.MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		if(payload < 0 || payload > ByteUtils.MAX_16_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		if(padding < 0 || padding > ByteUtils.MAX_16_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		ByteUtils.writeUint32(frame, header, 0);
		ByteUtils.writeUint16(payload, header, 4);
		ByteUtils.writeUint16(padding, header, 6);
	}

	static boolean validateHeader(byte[] header, long frame, int max) {
		if(header.length < TransportConstants.FRAME_HEADER_LENGTH)
			return false;
		if(ByteUtils.readUint32(header, 0) != frame) return false;
		int payload = ByteUtils.readUint16(header, 4);
		int padding = ByteUtils.readUint16(header, 6);
		if(payload + padding == 0) return false;
		if(payload + padding > max) return false;
		return true;
	}

	static int getPayloadLength(byte[] header) {
		if(header.length < TransportConstants.FRAME_HEADER_LENGTH)
			throw new IllegalArgumentException();
		return ByteUtils.readUint16(header, 4);
	}

	static int getPaddingLength(byte[] header) {
		if(header.length < TransportConstants.FRAME_HEADER_LENGTH)
			throw new IllegalArgumentException();
		return ByteUtils.readUint16(header, 6);
	}
}
