package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.autodelete.event.ConversationMessagesDeletedEvent;
import org.briarproject.briar.api.conversation.event.ConversationMessageReceivedEvent;
import org.briarproject.briar.api.sharing.InvitationResponse;
import org.briarproject.briar.api.sharing.Shareable;
import org.briarproject.briar.api.sharing.SharingManager;
import org.briarproject.briar.autodelete.AbstractAutoDeleteTest;

import static org.briarproject.bramble.api.cleanup.CleanupManager.BATCH_DELAY_MS;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.MIN_AUTO_DELETE_TIMER_MS;
import static org.briarproject.briar.test.TestEventListener.assertEvent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public abstract class AbstractAutoDeleteIntegrationTest
		extends AbstractAutoDeleteTest {

	protected SharingManager<? extends Shareable> sharingManager0;
	protected Shareable shareable;
	protected Class<? extends ConversationMessageReceivedEvent<? extends InvitationResponse>>
			responseReceivedEventClass;

	protected void testAutoDeclinedSharing(
			SharingManager<? extends Shareable> sharingManager0,
			Shareable shareable) throws Exception {
		setAutoDeleteTimer(c0, contactId1From0, MIN_AUTO_DELETE_TIMER_MS);

		// Send invitation
		sharingManager0.sendInvitation(
				shareable.getId(), contactId1From0, "This shareable!");

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
				assertEvent(c1, responseReceivedEventClass,
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
		assertTrue(sharingManager0
				.canBeShared(shareable.getId(), contact1From0));

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
		assertTrue(sharingManager0
				.canBeShared(shareable.getId(), contact1From0));
		// Send invitation
		sharingManager0.sendInvitation(shareable.getId(), contactId1From0,
				"This shareable, please be quick!");
		sync0To1(1, true);
		assertGroupCount(c1, contactId0From1, 1, 1);
	}
}
