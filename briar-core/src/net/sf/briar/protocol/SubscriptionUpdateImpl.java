package net.sf.briar.protocol;

import java.util.Map;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupId;
import net.sf.briar.api.protocol.SubscriptionUpdate;

class SubscriptionUpdateImpl implements SubscriptionUpdate {

	private final Map<GroupId, GroupId> holes;
	private final Map<Group, Long> subs;
	private final long expiry, timestamp;

	SubscriptionUpdateImpl(Map<GroupId, GroupId> holes, Map<Group, Long> subs,
			long expiry, long timestamp) {
		this.holes = holes;
		this.subs = subs;
		this.expiry = expiry;
		this.timestamp = timestamp;
	}

	public Map<GroupId, GroupId> getHoles() {
		return holes;
	}

	public Map<Group, Long> getSubscriptions() {
		return subs;
	}

	public long getExpiryTime() {
		return expiry;
	}

	public long getTimestamp() {
		return timestamp;
	}
}
