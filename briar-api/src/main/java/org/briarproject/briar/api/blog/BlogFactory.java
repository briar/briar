package org.briarproject.briar.api.blog;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;

@NotNullByDefault
public interface BlogFactory {

	/**
	 * Creates a personal blog for a given author.
	 */
	Blog createBlog(Author author);

	/**
	 * Parses a blog with the given Group
	 */
	Blog parseBlog(Group g) throws FormatException;

}
