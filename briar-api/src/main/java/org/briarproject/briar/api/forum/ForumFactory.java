package org.briarproject.briar.api.forum;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface ForumFactory {

	/**
	 * Creates a forum with the given name.
	 */
	Forum createForum(String name);

	/**
	 * Creates a forum with the given name and salt.
	 */
	Forum createForum(String name, byte[] salt);

}
