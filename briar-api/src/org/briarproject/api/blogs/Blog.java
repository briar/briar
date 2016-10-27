package org.briarproject.api.blogs;

import org.briarproject.api.clients.BaseGroup;
import org.briarproject.api.identity.Author;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sharing.Shareable;
import org.briarproject.api.sync.Group;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class Blog extends BaseGroup implements Shareable {

	private final Author author;

	public Blog(Group group, Author author) {
		super(group);
		this.author = author;
	}

	public Author getAuthor() {
		return author;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Blog && super.equals(o);
	}

	/**
	 * Returns the blog's author's name, not the name as shown in the UI.
	 */
	@Override
	public String getName() {
		return author.getName();
	}

}
