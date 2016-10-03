package org.briarproject.api.privategroup;

import org.briarproject.api.identity.Author;
import org.jetbrains.annotations.NotNull;

public interface PrivateGroupFactory {

	/**
	 * Creates a private group with the given name and author.
	 */
	@NotNull
	PrivateGroup createPrivateGroup(String name, Author author);

	/**
	 * Creates a private group with the given name, author and salt.
	 */
	@NotNull
	PrivateGroup createPrivateGroup(String name, Author author, byte[] salt);

}
