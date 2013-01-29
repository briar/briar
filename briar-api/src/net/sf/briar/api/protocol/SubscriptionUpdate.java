package net.sf.briar.api.protocol;

import java.util.Collection;

/** A packet updating the recipient's view of the sender's subscriptions. */
public class SubscriptionUpdate {

	private final Collection<Group> subs;
	private final long version;

	public SubscriptionUpdate(Collection<Group> subs, long version) {
		this.subs = subs;
		this.version = version;
	}

	/**
	 * Returns the groups to which the sender subscribes, and which the sender
	 * has made visible to the recipient.
	 */
	public Collection<Group> getGroups() {
		return subs;
	}

	/** Returns the update's version number. */
	public long getVersion() {
		return version;
	}
}
