package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

class TagDecoder {

	static boolean decodeTag(byte[] tag, int transportId, long connection,
			long packet) {
		if(tag.length != TAG_LENGTH) return false;
		// First 16 bits must be zero
		if(readUint16(tag, 0) != 0) return false;
		// Transport identifier is encoded as an unsigned 16-bit integer
		if(readUint16(tag, 2) != transportId) return false;
		// Connection number is encoded as an unsigned 32-bit integer
		if(readUint32(tag, 4) != connection) return false;
		// Packet number is encoded as an unsigned 32-bit integer
		if(readUint32(tag, 8) != packet) return false;
		// Last 32 bits must be zero
		if(readUint32(tag, 12) != 0L) return false;
		return true;
	}

	// Package access for testing
	static int readUint16(byte[] b, int offset) {
		assert b.length >= offset + 2;
		return ((b[offset] & 0xFF) << 8) | (b[offset + 1] & 0xFF);
	}

	// Package access for testing
	static long readUint32(byte[] b, int offset) {
		assert b.length >= offset + 4;
		return ((b[offset] & 0xFFL) << 24) | ((b[offset + 1] & 0xFFL) << 16)
		| ((b[offset + 2] & 0xFFL) << 8) | (b[offset + 3] & 0xFFL);
	}
}
