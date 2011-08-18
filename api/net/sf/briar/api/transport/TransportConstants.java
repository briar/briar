package net.sf.briar.api.transport;

public interface TransportConstants {

	/**
	 * The maximum length of a frame in bytes, including the header and footer.
	 */
	static final int MAX_FRAME_LENGTH = 65536; // 2^16

	/** The length in bytes of the tag that uniquely identifies a connection. */
	static final int TAG_LENGTH = 16;
}
