package org.briarproject.sync;

import org.briarproject.api.FormatException;
import org.briarproject.api.UniqueId;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;

import java.io.IOException;

import static org.briarproject.api.sync.SyncConstants.MAX_GROUP_DESCRIPTOR_LENGTH;

class GroupReader implements ObjectReader<Group> {

	private final GroupFactory groupFactory;

	GroupReader(GroupFactory groupFactory) {
		this.groupFactory = groupFactory;
	}

	public Group readObject(BdfReader r) throws IOException {
		r.readListStart();
		byte[] id = r.readRaw(UniqueId.LENGTH);
		if (id.length != UniqueId.LENGTH) throw new FormatException();
		byte[] descriptor = r.readRaw(MAX_GROUP_DESCRIPTOR_LENGTH);
		r.readListEnd();
		return groupFactory.createGroup(new ClientId(id), descriptor);
	}
}
