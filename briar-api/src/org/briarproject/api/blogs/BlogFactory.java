package org.briarproject.api.blogs;

import org.briarproject.api.FormatException;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.Group;
import org.jetbrains.annotations.NotNull;

public interface BlogFactory {

	/** Creates a blog with the given name, description and author. */
	Blog createBlog(@NotNull String name, @NotNull String description,
			@NotNull Author author);

	/** Creates a personal blog for a given author. */
	Blog createPersonalBlog(@NotNull Author author);

	/** Parses a blog with the given Group and description */
	Blog parseBlog(@NotNull Group g, @NotNull String description)
			throws FormatException;
}
