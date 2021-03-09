package org.briarproject.briar.sharing;

import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.forum.event.ForumInvitationResponseReceivedEvent;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Test;

public class AutoDeleteForumIntegrationTest
		extends AbstractAutoDeleteIntegrationTest {

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		shareable = c0.getForumManager().addForum("Test Forum");
		sharingManager0 = c0.getForumSharingManager();
		addContacts1And2();
		responseReceivedEventClass = ForumInvitationResponseReceivedEvent.class;
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
