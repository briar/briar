package net.sf.briar.api.protocol;

import java.util.Map;

/** A packet updating the sender's subscriptions. */
public interface SubscriptionUpdate {

	/** The maximum number of subscriptions per update. */
	static final int MAX_SUBS_PER_UPDATE = 6393;

	/** Returns the subscriptions contained in the update. */
	Map<Group, Long> getSubscriptions();

	/**
	 * Returns the update's timestamp. Updates that are older than the newest
	 * update received from the same contact must be ignored.
	 */
	long getTimestamp();
}
