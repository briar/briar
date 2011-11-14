package net.sf.briar.protocol;

import java.util.Map;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.SubscriptionUpdate;

interface SubscriptionUpdateFactory {

	SubscriptionUpdate createSubscriptions(Map<Group, Long> subs,
			long timestamp);
}
