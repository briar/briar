package org.briarproject.api.sync;

import java.util.Collection;

/** A packet updating the recipient's view of the sender's subscriptions. */
public class SubscriptionUpdate {

	private final Collection<Group> groups;
	private final long version;

	public SubscriptionUpdate(Collection<Group> groups, long version) {
		this.groups = groups;
		this.version = version;
	}

	/**
	 * Returns the groups to which the sender subscribes, and which the sender
	 * has made visible to the recipient.
	 */
	public Collection<Group> getGroups() {
		return groups;
	}

	/** Returns the update's version number. */
	public long getVersion() {
		return version;
	}
}
