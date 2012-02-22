package net.sf.briar.api.transport;

public interface TransportConstants {

	/** The length of the connection tag in bytes. */
	static final int TAG_LENGTH = 16;

	/** The maximum length of a frame in bytes, including the header and MAC. */
	static final int MAX_FRAME_LENGTH = 65536; // 2^16, 64 KiB

	/** The length of the frame header in bytes. */
	static final int FRAME_HEADER_LENGTH = 9;

	/** The length of the MAC in bytes. */
	static final int MAC_LENGTH = 48;

	/**
	 * The minimum connection length in bytes that all transport plugins must
	 * support. Connections may be shorter than this length, but all transport
	 * plugins must support connections of at least this length.
	 */
	static final int MIN_CONNECTION_LENGTH = 1024 * 1024; // 2^20, 1 MiB

	/** The size of the connection reordering window. */
	static final int CONNECTION_WINDOW_SIZE = 32;
}
