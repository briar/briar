package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.SubscriptionUpdate;

interface SubscriptionFactory {

	SubscriptionUpdate createSubscriptions(Collection<Group> subs, long timestamp);
}
