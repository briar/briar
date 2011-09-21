package net.sf.briar.api.protocol;

import net.sf.briar.api.transport.TransportConstants;

public interface ProtocolConstants {

	/**
	 * The maximum length of a serialised packet in bytes. To allow for future
	 * changes in the protocol, this is smaller than the minimum connection
	 * length minus the encryption and authentication overhead.
	 */
	static final int MAX_PACKET_LENGTH =
		TransportConstants.MIN_CONNECTION_LENGTH - 1024;
}
