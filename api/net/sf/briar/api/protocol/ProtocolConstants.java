package net.sf.briar.api.protocol;

public interface ProtocolConstants {

	/**
	 * The maximum length of a serialised packet in bytes. Since the protocol
	 * does not aim for low latency, the two main constraints here are the
	 * amount of memory used for parsing packets and the granularity of the
	 * database transactions for generating and receiving packets.
	 */
	static final int MAX_PACKET_LENGTH = 1024 * 1024; // 1 MiB
}
