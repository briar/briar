package org.briarproject.briar.api.test;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface TestDataCreator {

	/**
	 * Create fake test data on the DatabaseExecutor
	 *
	 * @param numContacts Number of contacts to create. Must be >= 1
	 * @param numPrivateMsgs Number of private messages to create for each
	 * contact.
	 * @param numBlogPosts Number of blog posts to create.
	 * @param numForums Number of forums to create.
	 * @param numForumPosts Number of forum posts to create per forum.
	 */
	void createTestData(int numContacts, int numPrivateMsgs, int numBlogPosts,
			int numForums, int numForumPosts);

}
