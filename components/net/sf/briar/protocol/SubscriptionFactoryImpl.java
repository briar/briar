package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.Subscriptions;

class SubscriptionFactoryImpl implements SubscriptionFactory {

	public Subscriptions createSubscriptions(Collection<Group> subs,
			long timestamp) {
		return new SubscriptionsImpl(subs, timestamp);
	}
}
