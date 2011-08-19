package net.sf.briar.api.transport;

public interface TransportConstants {

	/**
	 * The maximum length of a frame in bytes, including the header and footer.
	 */
	static final int MAX_FRAME_LENGTH = 65536; // 2^16

	/**
	 * The length in bytes of the encrypted IV that uniquely identifies a
	 * connection.
	 */
	static final int IV_LENGTH = 16;
}
