package org.briarproject.briar.sharing;

import org.briarproject.briar.api.blog.event.BlogInvitationResponseReceivedEvent;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Test;

public class AutoDeleteBlogIntegrationTest
		extends AbstractAutoDeleteIntegrationTest {

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		// personalBlog(author0) is already shared with c1
		shareable = c0.getBlogManager().getPersonalBlog(author2);
		sharingManager0 = c0.getBlogSharingManager();
		addContacts1And2();
		responseReceivedEventClass = BlogInvitationResponseReceivedEvent.class;
	}

	@Override
	protected ConversationManager.ConversationClient getConversationClient(
			BriarIntegrationTestComponent component) {
		return component.getBlogSharingManager();
	}

	@Test
	public void testAutoDeclinedBlogSharing() throws Exception {
		testAutoDeclinedSharing(sharingManager0, shareable);
	}
}
