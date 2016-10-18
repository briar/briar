package org.briarproject.api.blogs;

import org.briarproject.api.clients.BaseGroup;
import org.briarproject.api.identity.Author;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sharing.Shareable;
import org.briarproject.api.sync.Group;
import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
public class Blog extends BaseGroup implements Shareable {

	private final String description;
	private final Author author;

	public Blog(Group group, String name, String description, Author author) {
		super(group, name);
		this.description = description;
		this.author = author;
	}

	@NotNull
	public String getDescription() {
		return description;
	}

	@NotNull
	public Author getAuthor() {
		return author;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Blog && super.equals(o);
	}
}
