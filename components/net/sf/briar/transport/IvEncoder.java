package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;
import net.sf.briar.util.ByteUtils;

class IvEncoder {

	static byte[] encodeIv(int transportId, long connection) {
		byte[] iv = new byte[IV_LENGTH];
		// Encode the transport identifier as an unsigned 16-bit integer
		ByteUtils.writeUint16(transportId, iv, 4);
		// Encode the connection number as an unsigned 32-bit integer
		ByteUtils.writeUint32(connection, iv, 6);
		return iv;
	}

	static void encodeIv(byte[] iv, int transportId, long connection,
			long frame) {
		if(iv.length != IV_LENGTH) throw new IllegalArgumentException();
		// The first 16 bits of the IV must be zero (reserved)
		iv[0] = 0;
		iv[1] = 0;
		// Encode the transport identifier as an unsigned 16-bit integer
		ByteUtils.writeUint16(transportId, iv, 4);
		// Encode the connection number as an unsigned 32-bit integer
		ByteUtils.writeUint32(connection, iv, 6);
		// Encode the frame number as an unsigned 32-bit integer
		ByteUtils.writeUint32(frame, iv, 10);
		// The last 16 bits of the IV must be zero (block number)
		iv[14] = 0;
		iv[15] = 0;
	}
}
