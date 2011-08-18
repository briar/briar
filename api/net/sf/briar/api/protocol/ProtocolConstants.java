package net.sf.briar.api.protocol;

import net.sf.briar.api.transport.TransportConstants;

public interface ProtocolConstants {

	/**
	 * The maximum length of a serialised packet in bytes. To allow for future
	 * changes in the frame format, this is smaller than the amount of data
	 * that can fit in a frame using the current format.
	 */
	static final int MAX_PACKET_LENGTH =
		TransportConstants.MAX_FRAME_LENGTH - 1024;
}
