package org.briarproject.api.privategroup;

import org.briarproject.api.FormatException;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.Group;
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

	/**
	 * Parses a group and returns the corresponding PrivateGroup.
	 */
	PrivateGroup parsePrivateGroup(Group group) throws FormatException;

}
