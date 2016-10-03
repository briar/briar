package org.briarproject.api.privategroup;

import org.briarproject.api.clients.BaseGroup;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.Group;
import org.jetbrains.annotations.NotNull;

public class PrivateGroup extends BaseGroup {

	private final Author author;

	public PrivateGroup(@NotNull Group group, @NotNull String name,
			@NotNull Author author, @NotNull byte[] salt) {
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
