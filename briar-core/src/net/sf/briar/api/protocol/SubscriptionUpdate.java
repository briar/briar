package net.sf.briar.api.protocol;

import java.util.Map;

/** A packet updating the sender's subscriptions. */
public interface SubscriptionUpdate {

	/** Returns the holes contained in the update. */
	Map<GroupId, GroupId> getHoles();

	/** Returns the subscriptions contained in the update. */
	Map<Group, Long> getSubscriptions();

	/**
	 * Returns the expiry time of the contact's database. Messages that are
	 * older than the expiry time must not be sent to the contact.
	 */
	long getExpiryTime();

	/**
	 * Returns the update's timestamp. Updates that are older than the newest
	 * update received from the same contact must be ignored.
	 */
	long getTimestamp();
}
