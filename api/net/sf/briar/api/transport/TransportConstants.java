package net.sf.briar.api.transport;

public interface TransportConstants {

	/**
	 * The maximum length of a frame in bytes, including the header and footer.
	 */
	static final int MAX_FRAME_LENGTH = 65536; // 2^16, 64 KiB

	/**
	 * The length in bytes of the pseudo-random tag that uniquely identifies a
	 * connection.
	 */
	static final int TAG_LENGTH = 16;

	/**
	 * The minimum connection length in bytes that all transport plugins must
	 * support. Connections may be shorter than this length, but all transport
	 * plugins must support connections of at least this length.
	 */
	static final int MIN_CONNECTION_LENGTH = 1024 * 1024; // 2^20, 1 MiB
}
