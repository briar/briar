package org.briarproject.api.blogs;

import org.briarproject.api.forum.Forum;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.Group;
import org.jetbrains.annotations.NotNull;

public class Blog extends Forum {

	@NotNull
	private final String description;
	@NotNull
	private final Author author;

	public Blog(@NotNull Group group, @NotNull String name,
			@NotNull String description, @NotNull Author author) {
		super(group, name, null);

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
}
