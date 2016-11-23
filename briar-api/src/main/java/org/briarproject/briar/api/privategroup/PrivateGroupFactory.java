package org.briarproject.briar.api.privategroup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;

@NotNullByDefault
public interface PrivateGroupFactory {

	/**
	 * Creates a private group with the given name and author.
	 */
	PrivateGroup createPrivateGroup(String name, Author author);

	/**
	 * Creates a private group with the given name, author and salt.
	 */
	PrivateGroup createPrivateGroup(String name, Author author, byte[] salt);

	/**
	 * Parses a group and returns the corresponding PrivateGroup.
	 */
	PrivateGroup parsePrivateGroup(Group group) throws FormatException;

}
