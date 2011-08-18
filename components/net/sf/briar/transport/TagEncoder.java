package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import net.sf.briar.util.ByteUtils;

class TagEncoder {

	static byte[] encodeTag(int transportId, long connection) {
		byte[] tag = new byte[TAG_LENGTH];
		// Encode the transport identifier as an unsigned 16-bit integer
		ByteUtils.writeUint16(transportId, tag, 4);
		// Encode the connection number as an unsigned 32-bit integer
		ByteUtils.writeUint32(connection, tag, 6);
		return tag;
	}

	static void encodeTag(byte[] tag, int transportId, long connection,
			long frame) {
		if(tag.length != TAG_LENGTH) throw new IllegalArgumentException();
		// The first 16 bits of the tag must be zero (reserved)
		ByteUtils.writeUint16(0, tag, 0);
		// Encode the transport identifier as an unsigned 16-bit integer
		ByteUtils.writeUint16(transportId, tag, 4);
		// Encode the connection number as an unsigned 32-bit integer
		ByteUtils.writeUint32(connection, tag, 6);
		// Encode the frame number as an unsigned 32-bit integer
		ByteUtils.writeUint32(frame, tag, 10);
		// The last 16 bits of the tag must be zero (block number)
		ByteUtils.writeUint16(0, tag, 14);
	}
}
