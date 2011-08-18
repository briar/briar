package net.sf.briar.api.transport;

public interface TransportConstants {

	/**
	 * The maximum length of a frame in bytes, including the header and footer.
	 */
	static final int MAX_FRAME_LENGTH = 65536; // 2^16

	/** The length in bytes of the tag that uniquely identifies a connection. */
	static final int TAG_LENGTH = 16;

	/**
	 * The maximum value that can be represented as an unsigned 16-bit integer.
	 */
	static final int MAX_16_BIT_UNSIGNED = 65535; // 2^16 - 1

	/**
	 * The maximum value that can be represented as an unsigned 32-bit integer.
	 */
	static final long MAX_32_BIT_UNSIGNED = 4294967295L; // 2^32 - 1
}
