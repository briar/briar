package net.sf.briar.protocol;

import java.io.IOException;
import java.util.Map;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class SubscriptionReader implements ObjectReader<SubscriptionUpdate> {

	private final ObjectReader<Group> groupReader;
	private final SubscriptionFactory subscriptionFactory;

	SubscriptionReader(ObjectReader<Group> groupReader,
			SubscriptionFactory subscriptionFactory) {
		this.groupReader = groupReader;
		this.subscriptionFactory = subscriptionFactory;
	}

	public SubscriptionUpdate readObject(Reader r) throws IOException {
		// Initialise the consumer
		Consumer counting =
			new CountingConsumer(ProtocolConstants.MAX_PACKET_LENGTH);
		// Read the data
		r.addConsumer(counting);
		r.readUserDefinedTag(Tags.SUBSCRIPTION_UPDATE);
		r.addObjectReader(Tags.GROUP, groupReader);
		Map<Group, Long> subs = r.readMap(Group.class, Long.class);
		r.removeObjectReader(Tags.GROUP);
		long timestamp = r.readInt64();
		r.removeConsumer(counting);
		// Build and return the subscription update
		return subscriptionFactory.createSubscriptions(subs, timestamp);
	}
}
