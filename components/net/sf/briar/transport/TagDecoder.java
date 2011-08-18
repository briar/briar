package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import net.sf.briar.util.ByteUtils;

class TagDecoder {

	static boolean decodeTag(byte[] tag, int transportId, long connection) {
		if(tag.length != TAG_LENGTH) return false;
		// First 32 bits must be zero (reserved)
		for(int i = 0; i < 4; i++) if(tag[i] != 0) return false;
		// Transport identifier is encoded as an unsigned 16-bit integer
		if(ByteUtils.readUint16(tag, 4) != transportId) return false;
		// Connection number is encoded as an unsigned 32-bit integer
		if(ByteUtils.readUint32(tag, 6) != connection) return false;
		// Last 48 bits must be zero (frame number and block number)
		for(int i = 10; i < 16; i++) if(tag[i] != 0) return false;
		return true;
	}
}
