package net.sf.briar.api.protocol;

import java.util.Collection;

/** A packet updating the sender's transport properties. */
public interface TransportUpdate {

	/** Returns the transports contained in the update. */
	Collection<Transport> getTransports();

	/**
	 * Returns the update's timestamp. Updates that are older than the newest
	 * update received from the same contact must be ignored.
	 */
	long getTimestamp();
}
