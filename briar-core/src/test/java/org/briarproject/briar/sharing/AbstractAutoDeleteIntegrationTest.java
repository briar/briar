package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.autodelete.event.ConversationMessagesDeletedEvent;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.client.BaseGroup;
import org.briarproject.briar.api.conversation.event.ConversationMessageReceivedEvent;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.sharing.InvitationResponse;
import org.briarproject.briar.api.sharing.Shareable;
import org.briarproject.briar.api.sharing.SharingInvitationItem;
import org.briarproject.briar.api.sharing.SharingManager;
import org.briarproject.briar.autodelete.AbstractAutoDeleteTest;
import org.junit.Test;

import java.util.Collection;

import static org.briarproject.bramble.api.cleanup.CleanupManager.BATCH_DELAY_MS;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.MIN_AUTO_DELETE_TIMER_MS;
import static org.briarproject.briar.test.TestEventListener.assertEvent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public abstract class AbstractAutoDeleteIntegrationTest
		extends AbstractAutoDeleteTest {

	protected abstract SharingManager<? extends Shareable> getSharingManager0();

	protected abstract SharingManager<? extends Shareable> getSharingManager1();

	protected abstract Shareable getShareable();

	protected abstract Collection<? extends Shareable> subscriptions0()
			throws DbException;

	protected abstract Collection<? extends Shareable> subscriptions1()
			throws DbException;

	protected abstract Class<? extends ConversationMessageReceivedEvent<? extends InvitationResponse>> getResponseReceivedEventClass();

	@Test
	public void testAutoDeclinedSharing() throws Exception {
		setAutoDeleteTimer(c0, contactId1From0, MIN_AUTO_DELETE_TIMER_MS);

		// Send invitation
		getSharingManager0().sendInvitation(
				getShareable().getId(), contactId1From0, "This shareable!");

		// The message should have been added to 0's view of the conversation
		assertGroupCount(c0, contactId1From0, 1, 0);
		forEachHeader(c0, contactId1From0, 1, h -> {
			// The message should have the expected timer
			assertEquals(MIN_AUTO_DELETE_TIMER_MS, h.getAutoDeleteTimer());
		});

		// Sync invitation message
		sync0To1(1, true);
		ack1To0(1);
		waitForEvents(c0);

		// The message should have been added to 1's view of the conversation
		assertGroupCount(c1, contactId0From1, 1, 1);
		forEachHeader(c1, contactId0From1, 1, h -> {
			// The message should have the expected timer
			assertEquals(MIN_AUTO_DELETE_TIMER_MS, h.getAutoDeleteTimer());
		});

		// Both peers should be using the new timer
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				getAutoDeleteTimer(c0, contactId1From0));
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				getAutoDeleteTimer(c1, contactId0From1));

		// Before 0's timer elapses, both peers should still see the message
		long timerLatency = MIN_AUTO_DELETE_TIMER_MS + BATCH_DELAY_MS;
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertGroupCount(c0, contactId1From0, 1, 0);
		assertEquals(1, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 1, 1);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());

		// When 0's timer has elapsed, the message should be deleted from 0's
		// view of the conversation but 1 should still see the message
		ConversationMessagesDeletedEvent event =
				assertEvent(c0, ConversationMessagesDeletedEvent.class,
						() -> c0.getTimeTravel().addCurrentTimeMillis(1)
				);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		// assert that the proper event got broadcast￼
		assertEquals(contactId1From0, event.getContactId());

		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 1, 1);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());

		// 1 marks the message as read - this starts 1's timer
		final MessageId messageId0 =
				getMessageHeaders(c1, contactId0From1).get(0).getId();
		markMessageRead(c1, contact0From1, messageId0);
		assertGroupCount(c1, contactId0From1, 1, 0);

		// Before 1's timer elapses, 1 should still see the message
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 1, 0);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());

		// When 1's timer has elapsed, the message should be deleted from 1's
		// view of the conversation and the invitation auto-declined
		c0.getTimeTravel().addCurrentTimeMillis(1);
		ConversationMessageReceivedEvent<? extends InvitationResponse> e =
				assertEvent(c1, getResponseReceivedEventClass(),
						() -> c1.getTimeTravel().addCurrentTimeMillis(1)
				);
		// assert that the proper event got broadcast
		assertEquals(contactId0From1, e.getContactId());

		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 1, 0);
		forEachHeader(c1, contactId0From1, 1, h -> {
			// The only message is not the same as before, but declined response
			assertNotEquals(messageId0, h.getId());
			assertTrue(h instanceof InvitationResponse);
			assertEquals(h.getId(), e.getMessageHeader().getId());
			assertFalse(((InvitationResponse) h).wasAccepted());
			assertTrue(((InvitationResponse) h).isAutoDecline());
			// The auto-decline message should have the expected timer
			assertEquals(MIN_AUTO_DELETE_TIMER_MS,
					h.getAutoDeleteTimer());
		});

		// Sync the auto-decline message to 0
		sync1To0(1, true);
		// Sync the ack to 1 - this starts 1's timer
		ack0To1(1);
		waitForEvents(c1);

		// 0 can invite 1 again
		assertTrue(getSharingManager0()
				.canBeShared(getShareable().getId(), contact1From0));

		// Before 1's timer elapses, 1 should still see the auto-decline message
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertGroupCount(c0, contactId1From0, 1, 1);
		assertEquals(1, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 1, 0);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		// When 1's timer has elapsed, the auto-decline message should be
		// deleted from 1's view of the conversation
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 1, 1);
		assertEquals(1, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 0, 0);
		assertEquals(0, getMessageHeaders(c1, contactId0From1).size());

		// 0 marks the message as read - this starts 0's timer
		MessageId messageId1 =
				getMessageHeaders(c0, contactId1From0).get(0).getId();
		markMessageRead(c0, contact1From0, messageId1);
		assertGroupCount(c0, contactId1From0, 1, 0);

		// Before 0's timer elapses, 0 should still see the message
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertGroupCount(c0, contactId1From0, 1, 0);
		assertEquals(1, getMessageHeaders(c0, contactId1From0).size());

		// When 0's timer has elapsed, the message should be deleted from 0's
		// view of the conversation
		c0.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());

		// 0 can invite 1 again and really does invite
		assertTrue(getSharingManager0()
				.canBeShared(getShareable().getId(), contact1From0));
		// Send invitation
		getSharingManager0()
				.sendInvitation(getShareable().getId(), contactId1From0,
						"This shareable, please be quick!");
		sync0To1(1, true);
		assertGroupCount(c1, contactId0From1, 1, 1);
	}

	@Test
	public void testRespondAfterSenderDeletedInvitation() throws Exception {
		setAutoDeleteTimer(c0, contactId1From0, MIN_AUTO_DELETE_TIMER_MS);

		assertTrue(subscriptions0().contains(getShareable()));
		assertFalse(subscriptions1().contains(getShareable()));
		// what we expect after 1 accepts
		int expectedSubscriptions1 = subscriptions1().size() + 1;

		getSharingManager0().sendInvitation(
				getShareable().getId(), contactId1From0, "This shareable!");

		sync0To1(1, true);
		// 0's timer starts when it gets the ACK of the invitation
		ack1To0(1);
		waitForEvents(c0);
		assertGroupCount(c1, contactId0From1, 1, 1);

		// When 0's timer has elapsed, the message should be deleted from 0's
		// view of the conversation but 1 should still see the message
		long timerLatency = MIN_AUTO_DELETE_TIMER_MS + BATCH_DELAY_MS;
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 1, 1);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());

		// 1 marks as read, timer starting
		markMessageRead(c1, contact0From1,
				getMessageHeaders(c1, contactId0From1).get(0).getId());
		assertGroupCount(c1, contactId0From1, 1, 0);

		// 1 accepts the invitation that 0 has already deleted
		assertEquals(1, getSharingManager1().getInvitations().size());
		SharingInvitationItem invitation =
				getSharingManager1().getInvitations().iterator().next();
		assertEquals(getShareable(), invitation.getShareable());
		Contact c = contactManager1.getContact(contactId0From1);
		if (getShareable() instanceof Blog) {
			//noinspection unchecked
			((SharingManager<Blog>) getSharingManager1()).respondToInvitation(
					(Blog) getShareable(), c, true);
		} else if (getShareable() instanceof Forum) {
			//noinspection unchecked
			((SharingManager<Forum>) getSharingManager1()).respondToInvitation(
					(Forum) getShareable(), c, true);
		} else {
			fail();
		}

		// Sync the invitation response message to 0
		sync1To0(1, true);
		// 1's timer starts when it gets the ACK
		ack0To1(1);
		waitForEvents(c1);
		assertGroupCount(c0, contactId1From0, 1, 1);
		assertGroupCount(c1, contactId0From1, 2, 0);

		// 0 marks as read, timer starting
		markMessageRead(c0, contact1From0,
				getMessageHeaders(c0, contactId1From0).get(0).getId());
		assertGroupCount(c0, contactId1From0, 1, 0);
		assertGroupCount(c1, contactId0From1, 2, 0);

		// ensure accept message (invitation response) is linking to shareable
		InvitationResponse acceptMessage = (InvitationResponse)
				getMessageHeaders(c1, contactId0From1).get(1);
		assertEquals(acceptMessage.getShareableId(),
				((BaseGroup) getShareable()).getId());

		// both peers delete all messages after their timers expire
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 0, 0);
		assertEquals(0, getMessageHeaders(c1, contactId0From1).size());

		// there are no invitations hanging around
		assertEquals(0, getSharingManager0().getInvitations().size());
		assertEquals(0, getSharingManager1().getInvitations().size());

		assertEquals(expectedSubscriptions1, subscriptions1().size());
	}
}
