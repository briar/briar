package net.sf.briar.protocol;

import java.util.Map;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.SubscriptionUpdate;

class SubscriptionUpdateFactoryImpl implements SubscriptionUpdateFactory {

	public SubscriptionUpdate createSubscriptions(Map<Group, Long> subs,
			long timestamp) {
		return new SubscriptionUpdateImpl(subs, timestamp);
	}
}
