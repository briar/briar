package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;
import net.sf.briar.util.ByteUtils;

class IvEncoder {

	static byte[] encodeIv(int index, long connection) {
		byte[] iv = new byte[IV_LENGTH];
		// Encode the transport index as an unsigned 16-bit integer
		ByteUtils.writeUint16(index, iv, 4);
		// Encode the connection number as an unsigned 32-bit integer
		ByteUtils.writeUint32(connection, iv, 6);
		return iv;
	}

	static void updateIv(byte[] iv, long frame) {
		if(iv.length != IV_LENGTH) throw new IllegalArgumentException();
		// Encode the frame number as an unsigned 32-bit integer
		ByteUtils.writeUint32(frame, iv, 10);
	}

	static boolean validateIv(byte[] iv, int index, long connection) {
		if(iv.length != IV_LENGTH) return false;
		// Check that the reserved bits are all zero
		for(int i = 0; i < 3; i++) if(iv[i] != 0) return false;
		for(int i = 10; i < iv.length; i++) if(iv[i] != 0) return false;
		// Check that the transport index matches
		if(index != getTransportIndex(iv)) return false;
		// Check that the connection number matches
		if(connection != getConnectionNumber(iv)) return false;
		// The IV is valid
		return true;
	}

	static int getTransportIndex(byte[] iv) {
		if(iv.length != IV_LENGTH) throw new IllegalArgumentException();
		return ByteUtils.readUint16(iv, 4);
	}

	static long getConnectionNumber(byte[] iv) {
		if(iv.length != IV_LENGTH) throw new IllegalArgumentException();
		return ByteUtils.readUint32(iv, 6);
	}
}
