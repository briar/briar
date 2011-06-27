package net.sf.briar.api.db;

/** The status of a message with respect to a neighbour. */
public enum Status {
	/**
	 * The message has not been sent to, received from, or acked by the
	 * neighbour.
	 */
	NEW,
	/**
	 * The message has been sent to, but not received from or acked by, the
	 * neighbour.
	 */
	SENT,
	/** The message has been received from or acked by the neighbour. */
	SEEN
}
