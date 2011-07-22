package net.sf.briar.api.protocol;

import java.util.Map;

/** A packet updating the sender's transports. */
public interface Transports {

	/** Returns the transports contained in the update. */
	Map<String, String> getTransports();

	/**
	 * Returns the update's timestamp. Updates that are older than the newest
	 * update received from the same contact must be ignored.
	 */
	long getTimestamp();
}
