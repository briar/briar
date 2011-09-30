package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;
import net.sf.briar.api.TransportId;
import net.sf.briar.util.ByteUtils;

class IvEncoder {

	static byte[] encodeIv(boolean initiator, TransportId transport,
			long connection) {
		byte[] iv = new byte[IV_LENGTH];
		// Bit 31 is the initiator flag
		if(initiator) iv[3] = 1;
		// Encode the transport identifier as an unsigned 16-bit integer
		ByteUtils.writeUint16(transport.getInt(), iv, 4);
		// Encode the connection number as an unsigned 32-bit integer
		ByteUtils.writeUint32(connection, iv, 6);
		return iv;
	}

	static void updateIv(byte[] iv, long frame) {
		if(iv.length != IV_LENGTH) throw new IllegalArgumentException();
		// Encode the frame number as an unsigned 32-bit integer
		ByteUtils.writeUint32(frame, iv, 10);
	}
}
