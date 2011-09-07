package net.sf.briar.protocol;

import java.util.Map;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.SubscriptionUpdate;

class SubscriptionUpdateImpl implements SubscriptionUpdate {

	private final Map<Group, Long> subs;
	private final long timestamp;

	SubscriptionUpdateImpl(Map<Group, Long> subs, long timestamp) {
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
