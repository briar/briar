package net.sf.briar.protocol;

import java.util.Map;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.SubscriptionUpdate;

class SubscriptionFactoryImpl implements SubscriptionFactory {

	public SubscriptionUpdate createSubscriptions(Map<Group, Long> subs,
			long timestamp) {
		return new SubscriptionsImpl(subs, timestamp);
	}
}
