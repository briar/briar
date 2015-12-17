package org.briarproject.api.forum;

public interface ForumFactory {

	/** Creates a forum with the given name and a random salt. */
	Forum createForum(String name);
}
