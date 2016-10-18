package org.briarproject.api.forum;

import org.briarproject.api.clients.NamedGroup;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sharing.Shareable;
import org.briarproject.api.sync.Group;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
public class Forum extends NamedGroup implements Shareable {

	public Forum(Group group, String name, byte[] salt) {
		super(group, name, salt);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Forum && super.equals(o);
	}

}
