package net.sf.briar.transport;

public class TagEncoder {

	static byte[] encodeTag(int transportIdentifier, long connectionNumber,
			long packetNumber) {
		byte[] tag = new byte[Constants.TAG_BYTES];
		// Encode the transport identifier as an unsigned 16-bit integer
		writeUint16(transportIdentifier, tag, 2);
		// Encode the connection number as an unsigned 32-bit integer
		writeUint32(connectionNumber, tag, 4);
		// Encode the packet number as an unsigned 32-bit integer
		writeUint32(packetNumber, tag, 8);
		return tag;
	}

	// Package access for testing
	static void writeUint16(int i, byte[] b, int offset) {
		assert i >= 0;
		assert i <= Constants.MAX_16_BIT_UNSIGNED;
		assert b.length >= offset + 2;
		b[offset] = (byte) (i >> 8);
		b[offset + 1] = (byte) (i & 0xFF);
	}

	// Package access for testing
	static void writeUint32(long i, byte[] b, int offset) {
		assert i >= 0L;
		assert i <= Constants.MAX_32_BIT_UNSIGNED;
		assert b.length >= offset + 4;
		b[offset] = (byte) (i >> 24);
		b[offset + 1] = (byte) (i >> 16 & 0xFF);
		b[offset + 2] = (byte) (i >> 8 & 0xFF);
		b[offset + 3] = (byte) (i & 0xFF);
	}
}
