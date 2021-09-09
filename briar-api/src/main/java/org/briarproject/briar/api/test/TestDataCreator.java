package org.briarproject.briar.api.test;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface TestDataCreator {

	/**
	 * Create fake test data on the IoExecutor
	 *
	 * @param numContacts Number of contacts to create. Must be >= 1
	 * @param numPrivateMsgs Number of private messages to create for each
	 * contact.
	 * @param avatarPercent Percentage of contacts
	 * that will use a random profile image. Between 0 and 100.
	 * @param numBlogPosts Number of blog posts to create.
	 * @param numForums Number of forums to create.
	 * @param numForumPosts Number of forum posts to create per forum.
	 */
	void createTestData(int numContacts, int numPrivateMsgs, int avatarPercent,
			int numBlogPosts, int numForums, int numForumPosts);

	@IoExecutor
	Contact addContact(String name, boolean alias, boolean avatar)
			throws DbException;
}
