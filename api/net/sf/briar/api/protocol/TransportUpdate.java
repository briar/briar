package net.sf.briar.api.protocol;

import java.util.Map;

/** A packet updating the sender's transport properties. */
public interface TransportUpdate {

	/**
	 * The maximum size of a serialised transport update, excluding
	 * encryption and authentication.
	 */
	static final int MAX_SIZE = (1024 * 1024) - 100;

	/** Returns the transport properties contained in the update. */
	Map<String, Map<String, String>> getTransports();

	/**
	 * Returns the update's timestamp. Updates that are older than the newest
	 * update received from the same contact must be ignored.
	 */
	long getTimestamp();
}
