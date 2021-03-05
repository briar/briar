package org.briarproject.briar.sharing;

import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.forum.ForumManager;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Test;

public class AutoDeleteForumIntegrationTest
		extends AbstractAutoDeleteIntegrationTest {

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		ForumManager forumManager0 = c0.getForumManager();
		shareable = forumManager0.addForum("Test Forum");
		sharingManager0 = c0.getForumSharingManager();
		addContacts1And2();
	}

	@Override
	protected ConversationManager.ConversationClient getConversationClient(
			BriarIntegrationTestComponent component) {
		return component.getForumSharingManager();
	}

	@Test
	public void testAutoDeclinedForumSharing() throws Exception {
		testAutoDeclinedSharing(sharingManager0, shareable);
	}
}
