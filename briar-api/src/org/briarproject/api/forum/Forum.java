package org.briarproject.api.forum;

import org.briarproject.api.clients.BaseGroup;
import org.briarproject.api.sharing.Shareable;
import org.briarproject.api.sync.Group;

public class Forum extends BaseGroup implements Shareable {

	public Forum(Group group, String name, byte[] salt) {
		super(group, name, salt);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Forum && super.equals(o);
	}

}
