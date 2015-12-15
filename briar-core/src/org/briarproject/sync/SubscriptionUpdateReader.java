package org.briarproject.sync;

import org.briarproject.api.FormatException;
import org.briarproject.api.data.Consumer;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.data.Reader;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.SubscriptionUpdate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.briarproject.api.sync.MessagingConstants.MAX_PAYLOAD_LENGTH;
import static org.briarproject.api.sync.MessagingConstants.MAX_SUBSCRIPTIONS;

class SubscriptionUpdateReader implements ObjectReader<SubscriptionUpdate> {

	private final ObjectReader<Group> groupReader;

	SubscriptionUpdateReader(ObjectReader<Group> groupReader) {
		this.groupReader = groupReader;
	}

	public SubscriptionUpdate readObject(Reader r) throws IOException {
		// Set up the reader
		Consumer counting = new CountingConsumer(MAX_PAYLOAD_LENGTH);
		r.addConsumer(counting);
		// Read the start of the update
		r.readListStart();
		// Read the subscriptions, rejecting duplicates
		List<Group> groups = new ArrayList<Group>();
		Set<GroupId> ids = new HashSet<GroupId>();
		r.readListStart();
		for (int i = 0; i < MAX_SUBSCRIPTIONS && !r.hasListEnd(); i++) {
			Group g = groupReader.readObject(r);
			if (!ids.add(g.getId())) throw new FormatException(); // Duplicate
			groups.add(g);
		}
		r.readListEnd();
		// Read the version number
		long version = r.readInteger();
		if (version < 0) throw new FormatException();
		// Read the end of the update
		r.readListEnd();
		// Reset the reader
		r.removeConsumer(counting);
		// Build and return the subscription update
		groups = Collections.unmodifiableList(groups);
		return new SubscriptionUpdate(groups, version);
	}
}
