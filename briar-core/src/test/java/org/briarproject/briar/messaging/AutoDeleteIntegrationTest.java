package org.briarproject.briar.messaging;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.MessageDeletedException;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.attachment.AttachmentHeader;
import org.briarproject.briar.api.autodelete.AutoDeleteManager;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.conversation.ConversationManager.ConversationClient;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.briar.api.messaging.PrivateMessageFactory;
import org.briarproject.briar.autodelete.AbstractAutoDeleteTest;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.cleanup.CleanupManager.BATCH_DELAY_MS;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.MIN_AUTO_DELETE_TIMER_MS;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.briarproject.briar.messaging.MessagingConstants.MISSING_ATTACHMENT_CLEANUP_DURATION_MS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoDeleteIntegrationTest extends AbstractAutoDeleteTest {

	@Override
	protected ConversationClient getConversationClient(
			BriarIntegrationTestComponent component) {
		return component.getMessagingManager();
	}

	@Test
	public void testMessageWithoutTimer() throws Exception {
		// 0 creates a message without a timer
		MessageId messageId = createMessageWithoutTimer(c0, contactId1From0);
		// The message should have been added to 0's view of the conversation
		assertGroupCount(c0, contactId1From0, 1, 0);
		List<ConversationMessageHeader> headers0 =
				getMessageHeaders(c0, contactId1From0);
		assertEquals(1, headers0.size());
		ConversationMessageHeader h0 = headers0.get(0);
		assertEquals(messageId, h0.getId());
		// The message should not have a timer
		assertEquals(NO_AUTO_DELETE_TIMER, h0.getAutoDeleteTimer());
		// Sync the message to 1
		sync0To1(1, true);
		// The message should have been added to 1's view of the conversation
		assertGroupCount(c1, contactId0From1, 1, 1);
		List<ConversationMessageHeader> headers1 =
				getMessageHeaders(c1, contactId0From1);
		assertEquals(1, headers1.size());
		ConversationMessageHeader h1 = headers1.get(0);
		assertEquals(messageId, h1.getId());
		// The message should not have a timer
		assertEquals(NO_AUTO_DELETE_TIMER, h1.getAutoDeleteTimer());
	}

	@Test
	public void testDefaultTimer() throws Exception {
		// 0 creates a message with the default timer
		MessageId messageId = createMessageWithTimer(c0, contactId1From0);
		// The message should have been added to 0's view of the conversation
		assertGroupCount(c0, contactId1From0, 1, 0);
		List<ConversationMessageHeader> headers0 =
				getMessageHeaders(c0, contactId1From0);
		assertEquals(1, headers0.size());
		ConversationMessageHeader h0 = headers0.get(0);
		assertEquals(messageId, h0.getId());
		// The message should have the default timer (none)
		assertEquals(NO_AUTO_DELETE_TIMER, h0.getAutoDeleteTimer());
		// Sync the message to 1
		sync0To1(1, true);
		// Sync the ack to 0
		ack1To0(1);
		// The message should have been added to 1's view of the conversation
		assertGroupCount(c1, contactId0From1, 1, 1);
		List<ConversationMessageHeader> headers1 =
				getMessageHeaders(c1, contactId0From1);
		assertEquals(1, headers1.size());
		ConversationMessageHeader h1 = headers1.get(0);
		assertEquals(messageId, h1.getId());
		// The message should have the default timer (none)
		assertEquals(NO_AUTO_DELETE_TIMER, h1.getAutoDeleteTimer());
		// Both peers should still be using the default timer
		assertEquals(NO_AUTO_DELETE_TIMER,
				getAutoDeleteTimer(c0, contactId1From0));
		assertEquals(NO_AUTO_DELETE_TIMER,
				getAutoDeleteTimer(c1, contactId0From1));
	}

	@Test
	public void testNonDefaultTimer() throws Exception {
		// Set 0's timer
		setAutoDeleteTimer(c0, contactId1From0, MIN_AUTO_DELETE_TIMER_MS);
		// 0 should be using the new timer
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				getAutoDeleteTimer(c0, contactId1From0));
		// 1 should still be using the default timer
		assertEquals(NO_AUTO_DELETE_TIMER,
				getAutoDeleteTimer(c1, contactId0From1));
		// 0 creates a message with the new timer
		MessageId messageId = createMessageWithTimer(c0, contactId1From0);
		// The message should have been added to 0's view of the conversation
		assertGroupCount(c0, contactId1From0, 1, 0);
		List<ConversationMessageHeader> headers0 =
				getMessageHeaders(c0, contactId1From0);
		assertEquals(1, headers0.size());
		assertEquals(messageId, headers0.get(0).getId());
		// The message should have the new timer
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				headers0.get(0).getAutoDeleteTimer());
		// Sync the message to 1
		sync0To1(1, true);
		// Sync the ack to 0 - this starts 0's timer
		ack1To0(1);
		waitForEvents(c0);
		// The message should have been added to 1's view of the conversation
		assertGroupCount(c1, contactId0From1, 1, 1);
		List<ConversationMessageHeader> headers1 =
				getMessageHeaders(c1, contactId0From1);
		assertEquals(1, headers1.size());
		assertEquals(messageId, headers1.get(0).getId());
		// The message should have the new timer
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				headers1.get(0).getAutoDeleteTimer());
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
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 1, 1);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		// 1 marks the message as read - this starts 1's timer
		markMessageRead(c1, contact0From1, messageId);
		assertGroupCount(c1, contactId0From1, 1, 0);
		// Before 1's timer elapses, 1 should still see the message
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 1, 0);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		// When 1's timer has elapsed, the message should be deleted from 1's
		// view of the conversation
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 0, 0);
		assertEquals(0, getMessageHeaders(c1, contactId0From1).size());
	}

	@Test
	public void testTimerIsMirrored() throws Exception {
		// Set 0's timer
		setAutoDeleteTimer(c0, contactId1From0, MIN_AUTO_DELETE_TIMER_MS);
		// 0 should be using the new timer
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				getAutoDeleteTimer(c0, contactId1From0));
		// 1 should still be using the default timer
		assertEquals(NO_AUTO_DELETE_TIMER,
				getAutoDeleteTimer(c1, contactId0From1));
		// 0 creates a message with the new timer
		MessageId messageId0 = createMessageWithTimer(c0, contactId1From0);
		// The message should have been added to 0's view of the conversation
		assertGroupCount(c0, contactId1From0, 1, 0);
		List<ConversationMessageHeader> headers0 =
				getMessageHeaders(c0, contactId1From0);
		assertEquals(1, headers0.size());
		assertEquals(messageId0, headers0.get(0).getId());
		// The message should have the new timer
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				headers0.get(0).getAutoDeleteTimer());
		// Sync the message to 1
		sync0To1(1, true);
		// Sync the ack to 0 - this starts 0's timer
		ack1To0(1);
		waitForEvents(c0);
		// The message should have been added to 1's view of the conversation
		assertGroupCount(c1, contactId0From1, 1, 1);
		List<ConversationMessageHeader> headers1 =
				getMessageHeaders(c1, contactId0From1);
		assertEquals(1, headers1.size());
		assertEquals(messageId0, headers1.get(0).getId());
		// The message should have the new timer
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				headers1.get(0).getAutoDeleteTimer());
		// 0 and 1 should both be using the new timer
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
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 1, 1);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		// 1 marks the message as read - this starts 1's timer
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
		// view of the conversation
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 0, 0);
		assertEquals(0, getMessageHeaders(c1, contactId0From1).size());
		// 1 creates a message
		MessageId messageId1 = createMessageWithTimer(c1, contactId0From1);
		// The message should have been added to 1's view of the conversation
		assertGroupCount(c1, contactId0From1, 1, 0);
		headers1 = getMessageHeaders(c1, contactId0From1);
		assertEquals(1, headers1.size());
		assertEquals(messageId1, headers1.get(0).getId());
		// The message should have the new timer
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				headers1.get(0).getAutoDeleteTimer());
		// Sync the message to 0
		sync1To0(1, true);
		// Sync the ack to 1 - this starts 1's timer
		ack0To1(1);
		waitForEvents(c1);
		// The message should have been added to 0's view of the conversation
		assertGroupCount(c0, contactId1From0, 1, 1);
		headers0 = getMessageHeaders(c0, contactId1From0);
		assertEquals(1, headers0.size());
		assertEquals(messageId1, headers0.get(0).getId());
		// The message should have the new timer
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				headers0.get(0).getAutoDeleteTimer());
		// 0 and 1 should both be using the new timer
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				getAutoDeleteTimer(c0, contactId1From0));
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				getAutoDeleteTimer(c1, contactId0From1));
		// Before 1's timer elapses, both peers should still see the message
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertGroupCount(c0, contactId1From0, 1, 1);
		assertEquals(1, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 1, 0);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		// When 1's timer has elapsed, the message should be deleted from 1's
		// view of the conversation but 0 should still see the message
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 1, 1);
		assertEquals(1, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 0, 0);
		assertEquals(0, getMessageHeaders(c1, contactId0From1).size());
		// 0 marks the message as read - this starts 0's timer
		markMessageRead(c0, contact1From0, messageId1);
		assertGroupCount(c0, contactId1From0, 1, 0);
		// Before 0's timer elapses, 0 should still see the message
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertGroupCount(c0, contactId1From0, 1, 0);
		assertEquals(1, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 0, 0);
		assertEquals(0, getMessageHeaders(c1, contactId0From1).size());
		// When 0's timer has elapsed, the message should be deleted from 0's
		// view of the conversation
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertGroupCount(c1, contactId0From1, 0, 0);
		assertEquals(0, getMessageHeaders(c1, contactId0From1).size());
	}

	@Test
	public void testMessageWithAttachment() throws Exception {
		// Set 0's timer
		setAutoDeleteTimer(c0, contactId1From0, MIN_AUTO_DELETE_TIMER_MS);
		// 0 creates an attachment
		AttachmentHeader attachmentHeader =
				createAttachment(c0, contactId1From0);
		// 0 creates a message with the new timer and the attachment
		MessageId messageId = createMessageWithTimer(c0, contactId1From0,
				singletonList(attachmentHeader));
		// The message should have been added to 0's view of the conversation
		assertGroupCount(c0, contactId1From0, 1, 0);
		List<ConversationMessageHeader> headers0 =
				getMessageHeaders(c0, contactId1From0);
		assertEquals(1, headers0.size());
		assertEquals(messageId, headers0.get(0).getId());
		assertFalse(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		// Sync the message and the attachment to 1
		sync0To1(2, true);
		// Sync the acks to 0 - this starts 0's timer
		ack1To0(2);
		waitForEvents(c0);
		// The message should have been added to 1's view of the conversation
		assertGroupCount(c1, contactId0From1, 1, 1);
		List<ConversationMessageHeader> headers1 =
				getMessageHeaders(c1, contactId0From1);
		assertEquals(1, headers1.size());
		assertEquals(messageId, headers1.get(0).getId());
		assertFalse(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// Before 0's timer elapses, both peers should still see the message
		// and both should have the attachment
		long timerLatency = MIN_AUTO_DELETE_TIMER_MS + BATCH_DELAY_MS;
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertGroupCount(c0, contactId1From0, 1, 0);
		assertEquals(1, getMessageHeaders(c0, contactId1From0).size());
		assertFalse(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 1, 1);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		assertFalse(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// When 0's timer has elapsed, the message should be deleted from 0's
		// view of the conversation but 1 should still see the message
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertTrue(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 1, 1);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		assertFalse(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// 1 marks the message as read - this starts 1's timer
		markMessageRead(c1, contact0From1, messageId);
		assertGroupCount(c1, contactId0From1, 1, 0);
		// Before 1's timer elapses, 1 should still see the message
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertTrue(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 1, 0);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		assertFalse(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// When 1's timer has elapsed, the message should be deleted from 1's
		// view of the conversation
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertTrue(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 0, 0);
		assertEquals(0, getMessageHeaders(c1, contactId0From1).size());
		assertTrue(messageIsDeleted(c1, attachmentHeader.getMessageId()));
	}

	@Test
	public void testPrivateMessageWithMissingAttachmentIsDeleted()
			throws Exception {
		// Set 0's timer
		setAutoDeleteTimer(c0, contactId1From0, MIN_AUTO_DELETE_TIMER_MS);
		// 0 creates an attachment
		AttachmentHeader attachmentHeader =
				createAttachment(c0, contactId1From0);
		// 0 creates a message with the new timer and the attachment
		MessageId messageId = createMessageWithTimer(c0, contactId1From0,
				singletonList(attachmentHeader));
		// The message should have been added to 0's view of the conversation
		assertGroupCount(c0, contactId1From0, 1, 0);
		List<ConversationMessageHeader> headers0 =
				getMessageHeaders(c0, contactId1From0);
		assertEquals(1, headers0.size());
		assertEquals(messageId, headers0.get(0).getId());
		assertFalse(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		// Unshare the attachment so it won't be synced yet
		setMessageNotShared(c0, attachmentHeader.getMessageId());
		// Sync the message (but not the attachment) to 1
		sync0To1(1, true);
		// Sync the ack to 0 - this starts 0's timer
		ack1To0(1);
		waitForEvents(c0);
		// The message should have been added to 1's view of the conversation
		assertGroupCount(c1, contactId0From1, 1, 1);
		List<ConversationMessageHeader> headers1 =
				getMessageHeaders(c1, contactId0From1);
		assertEquals(1, headers1.size());
		assertEquals(messageId, headers1.get(0).getId());
		// Before 0's timer elapses, both peers should still see the message
		// and 0 should still have the attachment (1 hasn't received it)
		long timerLatency = MIN_AUTO_DELETE_TIMER_MS + BATCH_DELAY_MS;
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertGroupCount(c0, contactId1From0, 1, 0);
		assertEquals(1, getMessageHeaders(c0, contactId1From0).size());
		assertFalse(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 1, 1);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		// When 0's timer has elapsed, the message should be deleted from 0's
		// view of the conversation but 1 should still see the message
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertTrue(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 1, 1);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		// 1 marks the message as read - this starts 1's timer
		markMessageRead(c1, contact0From1, messageId);
		assertGroupCount(c1, contactId0From1, 1, 0);
		// Before 1's timer elapses, 1 should still see the message
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertTrue(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 1, 0);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		// When 1's timer has elapsed, the message should be deleted from 1's
		// view of the conversation
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertTrue(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 0, 0);
		assertEquals(0, getMessageHeaders(c1, contactId0From1).size());
	}

	@Test
	public void testOrphanedAttachmentIsDeleted() throws Exception {
		// Set 0's timer
		setAutoDeleteTimer(c0, contactId1From0, MIN_AUTO_DELETE_TIMER_MS);
		// 0 creates an attachment
		AttachmentHeader attachmentHeader =
				createAttachment(c0, contactId1From0);
		// 0 creates a message with the new timer and the attachment
		MessageId messageId = createMessageWithTimer(c0, contactId1From0,
				singletonList(attachmentHeader));
		// The message should have been added to 0's view of the conversation
		assertGroupCount(c0, contactId1From0, 1, 0);
		List<ConversationMessageHeader> headers0 =
				getMessageHeaders(c0, contactId1From0);
		assertEquals(1, headers0.size());
		assertEquals(messageId, headers0.get(0).getId());
		assertFalse(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		// Unshare the private message so it won't be synced yet
		setMessageNotShared(c0, messageId);
		// Sync the attachment (but not the message) to 1 - this starts 1's
		// orphan cleanup timer
		sync0To1(1, true);
		waitForEvents(c1);
		// Sync the ack to 0
		ack1To0(1);
		// The message should not have been added to 1's view of the
		// conversation
		assertGroupCount(c1, contactId0From1, 0, 0);
		List<ConversationMessageHeader> headers1 =
				getMessageHeaders(c1, contactId0From1);
		assertEquals(0, headers1.size());
		assertFalse(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// Before 1's timer elapses, both peers should still have the attachment
		long timerLatency =
				MISSING_ATTACHMENT_CLEANUP_DURATION_MS + BATCH_DELAY_MS;
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertFalse(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertFalse(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// When 1's timer has elapsed, 1 should no longer have the attachment
		// but 0 should still have it
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertFalse(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertTrue(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// Share the private message and sync it - too late to stop 1's orphan
		// cleanup timer
		setMessageShared(c0, messageId);
		sync0To1(1, true);
		// Sync the ack to 0 - this starts 0's timer
		ack1To0(1);
		waitForEvents(c0);
		// The message should have been added to 1's view of the conversation
		assertGroupCount(c1, contactId0From1, 1, 1);
		headers1 = getMessageHeaders(c1, contactId0From1);
		assertEquals(1, headers1.size());
		assertEquals(messageId, headers1.get(0).getId());
		assertTrue(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// Before 0's timer elapses, both peers should still see the message
		// and 0 should still have the attachment (1 has deleted it)
		timerLatency = MIN_AUTO_DELETE_TIMER_MS + BATCH_DELAY_MS;
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertGroupCount(c0, contactId1From0, 1, 0);
		assertEquals(1, getMessageHeaders(c0, contactId1From0).size());
		assertFalse(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 1, 1);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		assertTrue(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// When 0's timer has elapsed, the message should be deleted from 0's
		// view of the conversation but 1 should still see the message
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertTrue(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 1, 1);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		assertTrue(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// 1 marks the message as read - this starts 1's timer
		markMessageRead(c1, contact0From1, messageId);
		assertGroupCount(c1, contactId0From1, 1, 0);
		// Before 1's timer elapses, 1 should still see the message
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertTrue(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 1, 0);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		assertTrue(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// When 1's timer has elapsed, the message should be deleted from 1's
		// view of the conversation
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertTrue(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 0, 0);
		assertEquals(0, getMessageHeaders(c1, contactId0From1).size());
		assertTrue(messageIsDeleted(c1, attachmentHeader.getMessageId()));
	}

	@Test
	public void testOrphanedAttachmentIsNotDeletedIfPrivateMessageArrives()
			throws Exception {
		// Set 0's timer
		setAutoDeleteTimer(c0, contactId1From0, MIN_AUTO_DELETE_TIMER_MS);
		// 0 creates an attachment
		AttachmentHeader attachmentHeader =
				createAttachment(c0, contactId1From0);
		// 0 creates a message with the new timer and the attachment
		MessageId messageId = createMessageWithTimer(c0, contactId1From0,
				singletonList(attachmentHeader));
		// The message should have been added to 0's view of the conversation
		assertGroupCount(c0, contactId1From0, 1, 0);
		List<ConversationMessageHeader> headers0 =
				getMessageHeaders(c0, contactId1From0);
		assertEquals(1, headers0.size());
		assertEquals(messageId, headers0.get(0).getId());
		assertFalse(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		// Unshare the private message so it won't be synced yet
		setMessageNotShared(c0, messageId);
		// Sync the attachment (but not the message) to 1 - this starts 1's
		// orphan cleanup timer
		sync0To1(1, true);
		waitForEvents(c1);
		// Sync the ack to 0
		ack1To0(1);
		// The message should not have been added to 1's view of the
		// conversation
		assertGroupCount(c1, contactId0From1, 0, 0);
		List<ConversationMessageHeader> headers1 =
				getMessageHeaders(c1, contactId0From1);
		assertEquals(0, headers1.size());
		// Before 1's timer elapses, both peers should still have the attachment
		long timerLatency =
				MISSING_ATTACHMENT_CLEANUP_DURATION_MS + BATCH_DELAY_MS;
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertFalse(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertFalse(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// Share the private message and sync it - just in time to stop 1's
		// orphan cleanup timer
		setMessageShared(c0, messageId);
		sync0To1(1, true);
		// The message should have been added to 1's view of the conversation
		assertGroupCount(c1, contactId0From1, 1, 1);
		headers1 = getMessageHeaders(c1, contactId0From1);
		assertEquals(1, headers1.size());
		assertEquals(messageId, headers1.get(0).getId());
		assertFalse(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// When 1's timer has elapsed, both peers should still see the message
		// and both should still have the attachment
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 1, 0);
		assertEquals(1, getMessageHeaders(c0, contactId1From0).size());
		assertFalse(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 1, 1);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		assertFalse(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// Sync the ack to 0 - this starts 0's timer
		ack1To0(1);
		waitForEvents(c0);
		// Before 0's timer elapses, both peers should still see the message
		// and both should still have the attachment
		timerLatency = MIN_AUTO_DELETE_TIMER_MS + BATCH_DELAY_MS;
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertGroupCount(c0, contactId1From0, 1, 0);
		assertEquals(1, getMessageHeaders(c0, contactId1From0).size());
		assertFalse(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 1, 1);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		assertFalse(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// When 0's timer has elapsed, the message should be deleted from 0's
		// view of the conversation but 1 should still see the message
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertTrue(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 1, 1);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		assertFalse(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// 1 marks the message as read - this starts 1's timer
		markMessageRead(c1, contact0From1, messageId);
		assertGroupCount(c1, contactId0From1, 1, 0);
		// Before 1's timer elapses, 1 should still see the message
		c0.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		c1.getTimeTravel().addCurrentTimeMillis(timerLatency - 1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertTrue(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 1, 0);
		assertEquals(1, getMessageHeaders(c1, contactId0From1).size());
		assertFalse(messageIsDeleted(c1, attachmentHeader.getMessageId()));
		// When 1's timer has elapsed, the message should be deleted from 1's
		// view of the conversation
		c0.getTimeTravel().addCurrentTimeMillis(1);
		c1.getTimeTravel().addCurrentTimeMillis(1);
		assertGroupCount(c0, contactId1From0, 0, 0);
		assertEquals(0, getMessageHeaders(c0, contactId1From0).size());
		assertTrue(messageIsDeleted(c0, attachmentHeader.getMessageId()));
		assertGroupCount(c1, contactId0From1, 0, 0);
		assertEquals(0, getMessageHeaders(c1, contactId0From1).size());
		assertTrue(messageIsDeleted(c1, attachmentHeader.getMessageId()));
	}

	private MessageId createMessageWithoutTimer(
			BriarIntegrationTestComponent component, ContactId contactId)
			throws Exception {
		DatabaseComponent db = component.getDatabaseComponent();
		ConversationManager conversationManager =
				component.getConversationManager();
		MessagingManager messagingManager = component.getMessagingManager();
		PrivateMessageFactory factory = component.getPrivateMessageFactory();

		GroupId groupId = messagingManager.getConversationId(contactId);
		return db.transactionWithResult(false, txn -> {
			long timestamp = conversationManager
					.getTimestampForOutgoingMessage(txn, contactId);
			PrivateMessage m = factory.createPrivateMessage(groupId, timestamp,
					"Hi!", emptyList());
			messagingManager.addLocalMessage(txn, m);
			return m.getMessage().getId();
		});
	}

	private MessageId createMessageWithTimer(
			BriarIntegrationTestComponent component, ContactId contactId)
			throws Exception {
		return createMessageWithTimer(component, contactId, emptyList());
	}

	private MessageId createMessageWithTimer(
			BriarIntegrationTestComponent component, ContactId contactId,
			List<AttachmentHeader> attachmentHeaders) throws Exception {
		DatabaseComponent db = component.getDatabaseComponent();
		ConversationManager conversationManager =
				component.getConversationManager();
		AutoDeleteManager autoDeleteManager = component.getAutoDeleteManager();
		MessagingManager messagingManager = component.getMessagingManager();
		PrivateMessageFactory factory = component.getPrivateMessageFactory();

		GroupId groupId = messagingManager.getConversationId(contactId);
		return db.transactionWithResult(false, txn -> {
			long timestamp = conversationManager
					.getTimestampForOutgoingMessage(txn, contactId);
			long timer = autoDeleteManager
					.getAutoDeleteTimer(txn, contactId, timestamp);
			PrivateMessage m = factory.createPrivateMessage(groupId, timestamp,
					"Hi!", attachmentHeaders, timer);
			messagingManager.addLocalMessage(txn, m);
			return m.getMessage().getId();
		});
	}

	private AttachmentHeader createAttachment(
			BriarIntegrationTestComponent component, ContactId contactId)
			throws Exception {
		MessagingManager messagingManager = component.getMessagingManager();

		GroupId groupId = messagingManager.getConversationId(contactId);
		InputStream in = new ByteArrayInputStream(getRandomBytes(1234));
		return messagingManager.addLocalAttachment(groupId,
				component.getClock().currentTimeMillis(), "image/jpeg", in);
	}

	private boolean messageIsDeleted(BriarIntegrationTestComponent component,
			MessageId messageId) throws DbException {
		DatabaseComponent db = component.getDatabaseComponent();

		try {
			db.transaction(true, txn -> db.getMessage(txn, messageId));
			return false;
		} catch (MessageDeletedException e) {
			return true;
		}
	}
}
