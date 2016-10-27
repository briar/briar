package org.briarproject.api.blogs;

import org.briarproject.api.FormatException;
import org.briarproject.api.identity.Author;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.Group;
import org.jetbrains.annotations.NotNull;

@NotNullByDefault
public interface BlogFactory {

	/** Creates a personal blog for a given author. */
	Blog createBlog(Author author);

	/** Parses a blog with the given Group */
	Blog parseBlog(@NotNull Group g) throws FormatException;

}
