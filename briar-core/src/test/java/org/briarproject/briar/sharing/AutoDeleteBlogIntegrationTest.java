package org.briarproject.briar.sharing;

import org.briarproject.briar.api.blog.event.BlogInvitationResponseReceivedEvent;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.conversation.event.ConversationMessageReceivedEvent;
import org.briarproject.briar.api.sharing.InvitationResponse;
import org.briarproject.briar.api.sharing.Shareable;
import org.briarproject.briar.api.sharing.SharingManager;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Test;

public class AutoDeleteBlogIntegrationTest
		extends AbstractAutoDeleteIntegrationTest {

	private SharingManager<? extends Shareable> sharingManager0;
	private Shareable shareable;
	private Class<? extends ConversationMessageReceivedEvent<? extends InvitationResponse>>
			responseReceivedEventClass;

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

	@Override
	protected SharingManager<? extends Shareable> getSharingManager0() {
		return sharingManager0;
	}

	@Override
	protected Shareable getShareable() {
		return shareable;
	}

	@Override
	protected Class<? extends ConversationMessageReceivedEvent<? extends InvitationResponse>> getResponseReceivedEventClass() {
		return responseReceivedEventClass;
	}

	@Test
	public void testAutoDeclinedBlogSharing() throws Exception {
		testAutoDeclinedSharing();
	}
}
