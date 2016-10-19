package org.briarproject.api.privategroup;

import org.briarproject.api.clients.NamedGroup;
import org.briarproject.api.identity.Author;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.Group;
import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class PrivateGroup extends NamedGroup {

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
