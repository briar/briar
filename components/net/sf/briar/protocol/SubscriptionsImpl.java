package net.sf.briar.protocol;

import java.util.Map;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.SubscriptionUpdate;

class SubscriptionsImpl implements SubscriptionUpdate {

	private final Map<Group, Long> subs;
	private final long timestamp;

	SubscriptionsImpl(Map<Group, Long> subs, long timestamp) {
		this.subs = subs;
		this.timestamp = timestamp;
	}

	public Map<Group, Long> getSubscriptions() {
		return subs;
	}

	public long getTimestamp() {
		return timestamp;
	}
}
