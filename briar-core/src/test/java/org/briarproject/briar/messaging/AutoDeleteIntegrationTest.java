package org.briarproject.briar.messaging;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.briar.api.autodelete.AutoDeleteManager;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.briar.api.messaging.PrivateMessageFactory;
import org.briarproject.briar.test.BriarIntegrationTest;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.briarproject.briar.test.DaggerBriarIntegrationTestComponent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.MIN_AUTO_DELETE_TIMER_MS;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.junit.Assert.assertEquals;

public class AutoDeleteIntegrationTest
		extends BriarIntegrationTest<BriarIntegrationTestComponent> {

	@Override
	protected void createComponents() {
		BriarIntegrationTestComponent component =
				DaggerBriarIntegrationTestComponent.builder().build();
		BriarIntegrationTestComponent.Helper.injectEagerSingletons(component);
		component.inject(this);

		c0 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(t0Dir))
				.build();
		BriarIntegrationTestComponent.Helper.injectEagerSingletons(c0);

		c1 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(t1Dir))
				.build();
		BriarIntegrationTestComponent.Helper.injectEagerSingletons(c1);

		c2 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(t2Dir))
				.build();
		BriarIntegrationTestComponent.Helper.injectEagerSingletons(c2);
	}

	@Test
	public void testMessageWithoutTimer() throws Exception {
		// 0 creates a message without a timer
		MessageId messageId = createMessageWithoutTimer(c0, contactId1From0);
		// The message should have been added to 0's view of the conversation
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
		List<ConversationMessageHeader> headers0 =
				getMessageHeaders(c0, contactId1From0);
		assertEquals(1, headers0.size());
		ConversationMessageHeader h0 = headers0.get(0);
		assertEquals(messageId, h0.getId());
		// The message should have the default timer (none)
		assertEquals(NO_AUTO_DELETE_TIMER, h0.getAutoDeleteTimer());
		// Sync the message to 1
		sync0To1(1, true);
		// The message should have been added to 1's view of the conversation
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
		List<ConversationMessageHeader> headers0 =
				getMessageHeaders(c0, contactId1From0);
		assertEquals(1, headers0.size());
		ConversationMessageHeader h0 = headers0.get(0);
		assertEquals(messageId0, h0.getId());
		// The message should have the new timer
		assertEquals(MIN_AUTO_DELETE_TIMER_MS, h0.getAutoDeleteTimer());
		// Sync the message to 1
		sync0To1(1, true);
		// The message should have been added to 1's view of the conversation
		List<ConversationMessageHeader> headers1 =
				getMessageHeaders(c1, contactId0From1);
		assertEquals(1, headers1.size());
		ConversationMessageHeader h1 = headers1.get(0);
		assertEquals(messageId0, h1.getId());
		// The message should have the new timer
		assertEquals(MIN_AUTO_DELETE_TIMER_MS, h1.getAutoDeleteTimer());
		// 0 and 1 should both be using the new timer
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				getAutoDeleteTimer(c0, contactId1From0));
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				getAutoDeleteTimer(c1, contactId0From1));
		// 1 creates a message
		MessageId messageId1 = createMessageWithTimer(c1, contactId0From1);
		// The message should have been added to 1's view of the conversation
		headers1 = getMessageHeaders(c1, contactId0From1);
		assertEquals(2, headers1.size());
		assertEquals(messageId0, headers1.get(0).getId());
		assertEquals(messageId1, headers1.get(1).getId());
		// The message should have the new timer
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				headers1.get(1).getAutoDeleteTimer());
		// Sync the message to 0
		sync1To0(1, true);
		// The message should have been added to 0's view of the conversation
		headers0 = getMessageHeaders(c0, contactId1From0);
		assertEquals(2, headers0.size());
		assertEquals(messageId0, headers0.get(0).getId());
		assertEquals(messageId1, headers0.get(1).getId());
		// The message should have the new timer
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				headers0.get(1).getAutoDeleteTimer());
		// 0 and 1 should both be using the new timer
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				getAutoDeleteTimer(c0, contactId1From0));
		assertEquals(MIN_AUTO_DELETE_TIMER_MS,
				getAutoDeleteTimer(c1, contactId0From1));
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
					"Hi!", emptyList(), timer);
			messagingManager.addLocalMessage(txn, m);
			return m.getMessage().getId();
		});
	}

	private List<ConversationMessageHeader> getMessageHeaders(
			BriarIntegrationTestComponent component, ContactId contactId)
			throws Exception {
		DatabaseComponent db = component.getDatabaseComponent();
		MessagingManager messagingManager = component.getMessagingManager();

		return sortHeaders(db.transactionWithResult(true, txn ->
				messagingManager.getMessageHeaders(txn, contactId)));
	}

	private long getAutoDeleteTimer(BriarIntegrationTestComponent component,
			ContactId contactId) throws DbException {
		DatabaseComponent db = component.getDatabaseComponent();
		AutoDeleteManager autoDeleteManager = component.getAutoDeleteManager();

		return db.transactionWithResult(false,
				txn -> autoDeleteManager.getAutoDeleteTimer(txn, contactId));
	}

	private List<ConversationMessageHeader> sortHeaders(
			Collection<ConversationMessageHeader> in) {
		List<ConversationMessageHeader> out = new ArrayList<>(in);
		//noinspection UseCompareMethod
		out.sort((a, b) ->
				Long.valueOf(a.getTimestamp()).compareTo(b.getTimestamp()));
		return out;
	}
}
