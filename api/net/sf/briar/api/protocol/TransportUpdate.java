package net.sf.briar.api.protocol;

import java.util.Map;

/** A packet updating the sender's transport properties. */
public interface TransportUpdate {

	/** Returns the transport properties contained in the update. */
	Map<String, Map<String, String>> getTransports();

	/**
	 * Returns the update's timestamp. Updates that are older than the newest
	 * update received from the same contact must be ignored.
	 */
	long getTimestamp();
}
