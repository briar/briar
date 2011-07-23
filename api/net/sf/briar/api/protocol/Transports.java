package net.sf.briar.api.protocol;

import java.util.Map;

/** A packet updating the sender's transports. */
public interface Transports {

	/**
	 * The maximum size of a serialised transports update, excluding
	 * encryption and authentication.
	 */
	static final int MAX_SIZE = (1024 * 1024) - 100;

	/** Returns the transports contained in the update. */
	Map<String, String> getTransports();

	/**
	 * Returns the update's timestamp. Updates that are older than the newest
	 * update received from the same contact must be ignored.
	 */
	long getTimestamp();
}
