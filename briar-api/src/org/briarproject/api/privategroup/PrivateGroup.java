package org.briarproject.api.privategroup;

import org.briarproject.api.clients.NamedGroup;
import org.briarproject.api.identity.Author;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sharing.Shareable;
import org.briarproject.api.sync.Group;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class PrivateGroup extends NamedGroup implements Shareable {

	private final Author author;

	public PrivateGroup(Group group, String name, Author author, byte[] salt) {
		super(group, name, salt);
		this.author = author;
	}

	public Author getAuthor() {
		return author;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof PrivateGroup && super.equals(o);
	}

}
