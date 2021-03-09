package org.briarproject.briar.sharing;

import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.conversation.event.ConversationMessageReceivedEvent;
import org.briarproject.briar.api.forum.event.ForumInvitationResponseReceivedEvent;
import org.briarproject.briar.api.sharing.InvitationResponse;
import org.briarproject.briar.api.sharing.Shareable;
import org.briarproject.briar.api.sharing.SharingManager;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Test;

public class AutoDeleteForumIntegrationTest
		extends AbstractAutoDeleteIntegrationTest {

	private SharingManager<? extends Shareable> sharingManager0;
	protected Shareable shareable;
	protected Class<? extends ConversationMessageReceivedEvent<? extends InvitationResponse>>
			responseReceivedEventClass;

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
	public void testAutoDeclinedForumSharing() throws Exception {
		testAutoDeclinedSharing();
	}
}
