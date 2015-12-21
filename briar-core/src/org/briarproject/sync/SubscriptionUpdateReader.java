package org.briarproject.sync;

import org.briarproject.api.FormatException;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.SubscriptionUpdate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.briarproject.api.sync.SyncConstants.MAX_SUBSCRIPTIONS;

class SubscriptionUpdateReader implements ObjectReader<SubscriptionUpdate> {

	private final ObjectReader<Group> groupReader;

	SubscriptionUpdateReader(ObjectReader<Group> groupReader) {
		this.groupReader = groupReader;
	}

	public SubscriptionUpdate readObject(BdfReader r) throws IOException {
		r.readListStart();
		List<Group> groups = new ArrayList<Group>();
		Set<GroupId> ids = new HashSet<GroupId>();
		r.readListStart();
		for (int i = 0; i < MAX_SUBSCRIPTIONS && !r.hasListEnd(); i++) {
			Group g = groupReader.readObject(r);
			if (!ids.add(g.getId())) throw new FormatException(); // Duplicate
			groups.add(g);
		}
		r.readListEnd();
		long version = r.readInteger();
		if (version < 0) throw new FormatException();
		r.readListEnd();
		groups = Collections.unmodifiableList(groups);
		return new SubscriptionUpdate(groups, version);
	}
}
