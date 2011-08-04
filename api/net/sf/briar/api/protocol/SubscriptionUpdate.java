package net.sf.briar.api.protocol;

import java.util.Collection;

/** A packet updating the sender's subscriptions. */
public interface SubscriptionUpdate {

	/**
	 * The maximum size of a serialized subscription update, excluding
	 * encryption and authentication.
	 */
	static final int MAX_SIZE = (1024 * 1024) - 100;

	/** Returns the subscriptions contained in the update. */
	Collection<Group> getSubscriptions();

	/**
	 * Returns the update's timestamp. Updates that are older than the newest
	 * update received from the same contact must be ignored.
	 */
	long getTimestamp();
}
