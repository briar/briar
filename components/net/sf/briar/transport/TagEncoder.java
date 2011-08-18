package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_16_BIT_UNSIGNED;
import static net.sf.briar.api.transport.TransportConstants.MAX_32_BIT_UNSIGNED;

class TagEncoder {

	static byte[] encodeTag(int transportId, long connection,
			long packet) {
		byte[] tag = new byte[TAG_LENGTH];
		// Encode the transport identifier as an unsigned 16-bit integer
		writeUint16(transportId, tag, 2);
		// Encode the connection number as an unsigned 32-bit integer
		writeUint32(connection, tag, 4);
		// Encode the packet number as an unsigned 32-bit integer
		writeUint32(packet, tag, 8);
		return tag;
	}

	// Package access for testing
	static void writeUint16(int i, byte[] b, int offset) {
		assert i >= 0;
		assert i <= MAX_16_BIT_UNSIGNED;
		assert b.length >= offset + 2;
		b[offset] = (byte) (i >> 8);
		b[offset + 1] = (byte) (i & 0xFF);
	}

	// Package access for testing
	static void writeUint32(long i, byte[] b, int offset) {
		assert i >= 0L;
		assert i <= MAX_32_BIT_UNSIGNED;
		assert b.length >= offset + 4;
		b[offset] = (byte) (i >> 24);
		b[offset + 1] = (byte) (i >> 16 & 0xFF);
		b[offset + 2] = (byte) (i >> 8 & 0xFF);
		b[offset + 3] = (byte) (i & 0xFF);
	}
}
