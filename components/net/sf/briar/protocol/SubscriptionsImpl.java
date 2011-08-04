package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.SubscriptionUpdate;

class SubscriptionsImpl implements SubscriptionUpdate {

	private final Collection<Group> subs;
	private final long timestamp;

	SubscriptionsImpl(Collection<Group> subs, long timestamp) {
		this.subs = subs;
		this.timestamp = timestamp;
	}

	public Collection<Group> getSubscriptions() {
		return subs;
	}

	public long getTimestamp() {
		return timestamp;
	}
}
