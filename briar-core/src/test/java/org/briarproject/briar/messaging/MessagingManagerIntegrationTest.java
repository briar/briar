package org.briarproject.briar.messaging;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.MessageDeletedException;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.briar.api.attachment.AttachmentHeader;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.briar.api.messaging.PrivateMessageFactory;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;
import org.briarproject.briar.test.BriarIntegrationTest;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.briarproject.briar.test.DaggerBriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.MIN_AUTO_DELETE_TIMER_MS;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.briarproject.briar.test.BriarTestUtils.assertGroupCount;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MessagingManagerIntegrationTest
		extends BriarIntegrationTest<BriarIntegrationTestComponent> {

	private DatabaseComponent db0, db1;
	private MessagingManager messagingManager0, messagingManager1;
	private PrivateMessageFactory messageFactory;
	private ContactId contactId;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		db0 = c0.getDatabaseComponent();
		db1 = c1.getDatabaseComponent();
		messagingManager0 = c0.getMessagingManager();
		messagingManager1 = c1.getMessagingManager();
		messageFactory = c0.getPrivateMessageFactory();
		assertEquals(contactId0From1, contactId1From0);
		contactId = contactId0From1;
	}

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
	public void testSimpleConversation() throws Exception {
		// conversation starts out empty
		Collection<ConversationMessageHeader> messages0 = getMessages(c0);
		Collection<ConversationMessageHeader> messages1 = getMessages(c1);
		assertEquals(0, messages0.size());
		assertEquals(0, messages1.size());

		// message is sent/displayed properly
		String text = getRandomString(42);
		sendMessage(c0, c1, text);
		messages0 = getMessages(c0);
		messages1 = getMessages(c1);
		assertEquals(1, messages0.size());
		assertEquals(1, messages1.size());
		PrivateMessageHeader m0 =
				(PrivateMessageHeader) messages0.iterator().next();
		PrivateMessageHeader m1 =
				(PrivateMessageHeader) messages1.iterator().next();
		assertTrue(m0.hasText());
		assertTrue(m1.hasText());
		assertEquals(0, m0.getAttachmentHeaders().size());
		assertEquals(0, m1.getAttachmentHeaders().size());
		assertEquals(NO_AUTO_DELETE_TIMER, m0.getAutoDeleteTimer());
		assertEquals(NO_AUTO_DELETE_TIMER, m1.getAutoDeleteTimer());
		assertTrue(m0.isRead());
		assertFalse(m1.isRead());
		assertGroupCounts(c0, 1, 0);
		assertGroupCounts(c1, 1, 1);

		// same for reply
		String text2 = getRandomString(42);
		sendMessage(c1, c0, text2);
		messages0 = getMessages(c0);
		messages1 = getMessages(c1);
		assertEquals(2, messages0.size());
		assertEquals(2, messages1.size());
		assertGroupCounts(c0, 2, 1);
		assertGroupCounts(c1, 2, 1);
	}

	@Test
	public void testAttachments() throws Exception {
		// send message with attachment
		AttachmentHeader h = addAttachment(c0);
		sendMessage(c0, c1, null, singletonList(h));

		// message with attachment is sent/displayed properly
		Collection<ConversationMessageHeader> messages0 = getMessages(c0);
		Collection<ConversationMessageHeader> messages1 = getMessages(c1);
		assertEquals(1, messages0.size());
		assertEquals(1, messages1.size());
		PrivateMessageHeader m0 =
				(PrivateMessageHeader) messages0.iterator().next();
		PrivateMessageHeader m1 =
				(PrivateMessageHeader) messages1.iterator().next();
		assertFalse(m0.hasText());
		assertFalse(m1.hasText());
		assertEquals(1, m0.getAttachmentHeaders().size());
		assertEquals(1, m1.getAttachmentHeaders().size());
		assertEquals(NO_AUTO_DELETE_TIMER, m0.getAutoDeleteTimer());
		assertEquals(NO_AUTO_DELETE_TIMER, m1.getAutoDeleteTimer());
		assertTrue(m0.isRead());
		assertFalse(m1.isRead());
		assertGroupCounts(c0, 1, 0);
		assertGroupCounts(c1, 1, 1);
	}

	@Test
	public void testAutoDeleteTimer() throws Exception {
		// send message with auto-delete timer
		sendMessage(c0, c1, getRandomString(123), emptyList(),
				MIN_AUTO_DELETE_TIMER_MS);

		// message with timer is sent/displayed properly
		Collection<ConversationMessageHeader> messages0 = getMessages(c0);
		Collection<ConversationMessageHeader> messages1 = getMessages(c1);
		assertEquals(1, messages0.size());
		assertEquals(1, messages1.size());
		PrivateMessageHeader m0 =
				(PrivateMessageHeader) messages0.iterator().next();
		PrivateMessageHeader m1 =
				(PrivateMessageHeader) messages1.iterator().next();
		assertTrue(m0.hasText());
		assertTrue(m1.hasText());
		assertEquals(0, m0.getAttachmentHeaders().size());
		assertEquals(0, m1.getAttachmentHeaders().size());
		assertEquals(MIN_AUTO_DELETE_TIMER_MS, m0.getAutoDeleteTimer());
		assertEquals(MIN_AUTO_DELETE_TIMER_MS, m1.getAutoDeleteTimer());
		assertTrue(m0.isRead());
		assertFalse(m1.isRead());
		assertGroupCounts(c0, 1, 0);
		assertGroupCounts(c1, 1, 1);
	}

	@Test
	public void testDeleteAll() throws Exception {
		// send 3 messages (1 with attachment)
		sendMessage(c0, c1, getRandomString(42));
		sendMessage(c0, c1, getRandomString(23));
		sendMessage(c0, c1, null, singletonList(addAttachment(c0)));
		assertEquals(3, getMessages(c0).size());
		assertEquals(3, getMessages(c1).size());
		assertGroupCounts(c0, 3, 0);
		assertGroupCounts(c1, 3, 3);

		// delete all messages on both sides (deletes all, because returns true)
		assertTrue(db0.transactionWithResult(false,
				txn -> messagingManager0.deleteAllMessages(txn, contactId))
				.allDeleted());
		assertTrue(db1.transactionWithResult(false,
				txn -> messagingManager1.deleteAllMessages(txn, contactId))
				.allDeleted());

		// all messages are gone
		assertEquals(0, getMessages(c0).size());
		assertEquals(0, getMessages(c1).size());
		assertGroupCounts(c0, 0, 0);
		assertGroupCounts(c1, 0, 0);
	}

	@Test
	public void testDeleteSubset() throws Exception {
		// send 3 message (1 with attachment)
		PrivateMessage m0 = sendMessage(c0, c1, getRandomString(42));
		PrivateMessage m1 = sendMessage(c0, c1, getRandomString(23));
		PrivateMessage m2 =
				sendMessage(c0, c1, null, singletonList(addAttachment(c0)));
		assertGroupCounts(c0, 3, 0);
		assertGroupCounts(c1, 3, 3);

		// delete 2 messages on both sides (deletes all, because returns true)
		Set<MessageId> toDelete = new HashSet<>();
		toDelete.add(m1.getMessage().getId());
		toDelete.add(m2.getMessage().getId());
		assertTrue(db0.transactionWithResult(false, txn ->
				messagingManager0.deleteMessages(txn, contactId, toDelete))
				.allDeleted());
		assertTrue(db1.transactionWithResult(false, txn ->
				messagingManager1.deleteMessages(txn, contactId, toDelete))
				.allDeleted());

		// all messages except 1 are gone
		assertEquals(1, getMessages(c0).size());
		assertEquals(1, getMessages(c1).size());
		assertEquals(m0.getMessage().getId(),
				getMessages(c0).iterator().next().getId());
		assertEquals(m0.getMessage().getId(),
				getMessages(c1).iterator().next().getId());
		assertGroupCounts(c0, 1, 0);
		assertGroupCounts(c1, 1, 1);

		// remove also last message
		toDelete.clear();
		toDelete.add(m0.getMessage().getId());
		assertTrue(db0.transactionWithResult(false, txn ->
				messagingManager0.deleteMessages(txn, contactId, toDelete))
				.allDeleted());
		assertEquals(0, getMessages(c0).size());
		assertGroupCounts(c0, 0, 0);
	}

	@Test
	public void testDeleteLegacySubset() throws Exception {
		// send legacy message
		GroupId g = c0.getMessagingManager().getConversationId(contactId);
		PrivateMessage m0 = messageFactory.createLegacyPrivateMessage(g,
				c0.getClock().currentTimeMillis(), getRandomString(42));
		c0.getMessagingManager().addLocalMessage(m0);
		syncMessage(c0, c1, contactId, 1, true);

		// message arrived on both sides
		assertEquals(1, getMessages(c0).size());
		assertEquals(1, getMessages(c1).size());

		// delete message on both sides (deletes all, because returns true)
		Set<MessageId> toDelete = new HashSet<>();
		toDelete.add(m0.getMessage().getId());
		assertTrue(c0.getConversationManager()
				.deleteMessages(contactId, toDelete).allDeleted());
		assertTrue(c1.getConversationManager()
				.deleteMessages(contactId, toDelete).allDeleted());

		// message was deleted
		assertEquals(0, getMessages(c0).size());
		assertEquals(0, getMessages(c1).size());
	}

	@Test
	public void testDeleteAttachment() throws Exception {
		// send one message with attachment
		AttachmentHeader h = addAttachment(c0);
		sendMessage(c0, c1, getRandomString(42), singletonList(h));

		// attachment exists on both devices
		db0.transaction(true, txn -> db0.getMessage(txn, h.getMessageId()));
		db1.transaction(true, txn -> db1.getMessage(txn, h.getMessageId()));

		// delete message on both sides (deletes all, because returns true)
		assertTrue(db0.transactionWithResult(false,
				txn -> messagingManager0.deleteAllMessages(txn, contactId))
				.allDeleted());
		assertTrue(db1.transactionWithResult(false,
				txn -> messagingManager1.deleteAllMessages(txn, contactId))
				.allDeleted());

		// attachment was deleted on both devices
		try {
			db0.transaction(true, txn -> db0.getMessage(txn, h.getMessageId()));
			fail();
		} catch (MessageDeletedException e) {
			// expected
		}
		try {
			db1.transaction(true, txn -> db1.getMessage(txn, h.getMessageId()));
			fail();
		} catch (MessageDeletedException e) {
			// expected
		}
	}

	@Test
	public void testDeletingEmptySet() throws Exception {
		assertTrue(db0.transactionWithResult(false, txn ->
				messagingManager0.deleteMessages(txn, contactId, emptySet()))
				.allDeleted());
	}

	private PrivateMessage sendMessage(BriarIntegrationTestComponent from,
			BriarIntegrationTestComponent to, String text) throws Exception {
		return sendMessage(from, to, text, emptyList());
	}

	private PrivateMessage sendMessage(BriarIntegrationTestComponent from,
			BriarIntegrationTestComponent to, @Nullable String text,
			List<AttachmentHeader> attachments) throws Exception {
		return sendMessage(from, to, text, attachments, NO_AUTO_DELETE_TIMER);
	}

	private PrivateMessage sendMessage(BriarIntegrationTestComponent from,
			BriarIntegrationTestComponent to, @Nullable String text,
			List<AttachmentHeader> attachments, long autoDeleteTimer)
			throws Exception {
		GroupId g = from.getMessagingManager().getConversationId(contactId);
		PrivateMessage m = messageFactory.createPrivateMessage(g,
				from.getClock().currentTimeMillis(), text, attachments,
				autoDeleteTimer);
		from.getMessagingManager().addLocalMessage(m);
		syncMessage(from, to, contactId, 1 + attachments.size(), true);
		return m;
	}

	private AttachmentHeader addAttachment(BriarIntegrationTestComponent c)
			throws Exception {
		GroupId g = c.getMessagingManager().getConversationId(contactId);
		InputStream stream = new ByteArrayInputStream(getRandomBytes(42));
		return c.getMessagingManager().addLocalAttachment(g,
				c.getClock().currentTimeMillis(), "image/jpeg", stream);
	}

	private Collection<ConversationMessageHeader> getMessages(
			BriarIntegrationTestComponent c)
			throws Exception {
		Collection<ConversationMessageHeader> messages =
				c.getDatabaseComponent().transactionWithResult(true,
						txn -> c.getMessagingManager()
								.getMessageHeaders(txn, contactId));
		Set<MessageId> ids =
				c.getDatabaseComponent().transactionWithResult(true,
						txn ->
								c.getMessagingManager()
										.getMessageIds(txn, contactId));
		assertEquals(messages.size(), ids.size());
		for (ConversationMessageHeader h : messages) {
			assertTrue(ids.contains(h.getId()));
		}
		return messages;
	}

	private void assertGroupCounts(BriarIntegrationTestComponent c,
			long msgCount, long unreadCount) throws Exception {
		GroupId g = c.getMessagingManager().getConversationId(contactId);
		assertGroupCount(c.getMessageTracker(), g, msgCount, unreadCount);
	}


}
