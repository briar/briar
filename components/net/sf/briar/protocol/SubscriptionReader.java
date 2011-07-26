package net.sf.briar.protocol;

import java.io.IOException;
import java.util.Collection;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.Subscriptions;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

import com.google.inject.Inject;

class SubscriptionReader implements ObjectReader<Subscriptions> {

	private final ObjectReader<Group> groupReader;
	private final SubscriptionFactory subscriptionFactory;

	@Inject
	SubscriptionReader(ObjectReader<Group> groupReader,
			SubscriptionFactory subscriptionFactory) {
		this.groupReader = groupReader;
		this.subscriptionFactory = subscriptionFactory;
	}

	public Subscriptions readObject(Reader r) throws IOException {
		// Initialise the consumer
		CountingConsumer counting =
			new CountingConsumer(Subscriptions.MAX_SIZE);
		// Read the data
		r.addConsumer(counting);
		r.readUserDefinedTag(Tags.SUBSCRIPTIONS);
		r.addObjectReader(Tags.GROUP, groupReader);
		Collection<Group> subs = r.readList(Group.class);
		r.removeObjectReader(Tags.GROUP);
		long timestamp = r.readInt64();
		r.removeConsumer(counting);
		// Build and return the subscriptions update
		return subscriptionFactory.createSubscriptions(subs, timestamp);
	}
}
