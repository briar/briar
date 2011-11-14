package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.util.ByteUtils;

class IvEncoder {

	static byte[] encodeIv(boolean initiator, TransportIndex i,
			long connection) {
		byte[] iv = new byte[IV_LENGTH];
		// Bit 31 is the initiator flag
		if(initiator) iv[3] = 1;
		// Encode the transport identifier as an unsigned 16-bit integer
		ByteUtils.writeUint16(i.getInt(), iv, 4);
		// Encode the connection number as an unsigned 32-bit integer
		ByteUtils.writeUint32(connection, iv, 6);
		return iv;
	}

	static void updateIv(byte[] iv, long frame) {
		if(iv.length != IV_LENGTH) throw new IllegalArgumentException();
		// Encode the frame number as an unsigned 32-bit integer
		ByteUtils.writeUint32(frame, iv, 10);
	}

	static boolean validateIv(byte[] iv, boolean initiator, TransportIndex i) {
		if(iv.length != IV_LENGTH) return false;
		// Check that the reserved bits are all zero
		for(int j = 0; j < 2; j++) if(iv[j] != 0) return false;
		if(iv[3] != 0 && iv[3] != 1) return false;
		for(int j = 10; j < iv.length; j++) if(iv[j] != 0) return false;
		// Check that the initiator flag matches
		if(initiator != getInitiatorFlag(iv)) return false;
		// Check that the transport ID matches
		if(i.getInt() != getTransportId(iv)) return false;
		// The IV is valid
		return true;
	}

	static boolean getInitiatorFlag(byte[] iv) {
		if(iv.length != IV_LENGTH) throw new IllegalArgumentException();
		return (iv[3] & 1) == 1;
	}

	static int getTransportId(byte[] iv) {
		if(iv.length != IV_LENGTH) throw new IllegalArgumentException();
		return ByteUtils.readUint16(iv, 4);
	}

	static long getConnectionNumber(byte[] iv) {
		if(iv.length != IV_LENGTH) throw new IllegalArgumentException();
		return ByteUtils.readUint32(iv, 6);
	}
}
