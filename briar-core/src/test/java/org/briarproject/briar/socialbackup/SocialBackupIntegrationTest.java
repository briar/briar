package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.socialbackup.BackupMetadata;
import org.briarproject.briar.api.socialbackup.ShardMessageHeader;
import org.briarproject.briar.api.socialbackup.SocialBackupManager;
import org.briarproject.briar.test.BriarIntegrationTest;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.briarproject.briar.test.DaggerBriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SocialBackupIntegrationTest
		extends BriarIntegrationTest<BriarIntegrationTestComponent> {

	private SocialBackupManager socialBackupManager0;
	private SocialBackupManager socialBackupManager1;
	private SocialBackupManager socialBackupManager2;

	private Group g1From0;
	private Group g0From1;
	private Group g2From0;
	private Group g0From2;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		socialBackupManager0 = c0.getSocialBackupManager();
		socialBackupManager1 = c1.getSocialBackupManager();
		socialBackupManager2 = c2.getSocialBackupManager();

		g1From0 = socialBackupManager0.getContactGroup(contact1From0);
		g0From1 = socialBackupManager1.getContactGroup(contact0From1);
		g2From0 = socialBackupManager0.getContactGroup(contact2From0);
		g0From2 = socialBackupManager2.getContactGroup(contact0From2);
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
	public void testCreateBackup() throws Exception {
		// Create the backup
		db0.transaction(false, txn -> {
			assertNull(socialBackupManager0.getBackupMetadata(txn));
			socialBackupManager0.createBackup(txn,
					asList(contactId1From0, contactId2From0), 2);
			BackupMetadata backupMetadata =
					socialBackupManager0.getBackupMetadata(txn);
			assertNotNull(backupMetadata);
			List<Author> expected = asList(contact1From0.getAuthor(),
					contact2From0.getAuthor());
			assertEquals(expected, backupMetadata.getCustodians());
			assertEquals(2, backupMetadata.getThreshold());
			assertEquals(0, backupMetadata.getVersion());
		});
		// Sync the shard and backup messages to the contacts
		sync0To1(2, true);
		sync0To2(2, true);

		Collection<ConversationMessageHeader> messages1At0 =
				getMessages1At0();
		assertEquals(1, messages1At0.size());
		for (ConversationMessageHeader h : messages1At0) {
			assertTrue(h instanceof ShardMessageHeader);
			ShardMessageHeader s = (ShardMessageHeader) h;
			assertTrue(s.isLocal());
		}

		Collection<ConversationMessageHeader> messages2At0 =
				getMessages2At0();
		assertEquals(1, messages2At0.size());
		for (ConversationMessageHeader h : messages2At0) {
			assertTrue(h instanceof ShardMessageHeader);
			ShardMessageHeader s = (ShardMessageHeader) h;
			assertTrue(s.isLocal());
		}

		// the shard message from 0 should have arrived at 1
		Collection<ConversationMessageHeader> messages0At1 =
				getMessages0At1();
		assertEquals(1, messages0At1.size());
		for (ConversationMessageHeader h : messages0At1) {
			assertTrue(h instanceof ShardMessageHeader);
			ShardMessageHeader s = (ShardMessageHeader) h;
			assertFalse(s.isLocal());
		}
        db1.transaction(false, txn -> {
        	assertTrue(socialBackupManager1.amCustodian(txn, contactId0From1));
        });

		// the shard message from 0 should have arrived at 2
		Collection<ConversationMessageHeader> messages0At2 =
				getMessages0At2();
		assertEquals(1, messages0At2.size());
		for (ConversationMessageHeader h : messages0At2) {
			assertTrue(h instanceof ShardMessageHeader);
			ShardMessageHeader s = (ShardMessageHeader) h;
			assertFalse(s.isLocal());
		}

		// assert group counts
		assertGroupCount(messageTracker0, g1From0.getId(), 1, 0);
		assertGroupCount(messageTracker0, g2From0.getId(), 1, 0);
		assertGroupCount(messageTracker1, g0From1.getId(), 1, 1);
		assertGroupCount(messageTracker2, g0From2.getId(), 1, 1);

		// mark a message as read
		socialBackupManager1.setReadFlag(g0From1.getId(),
				messages0At1.iterator().next().getId(), true);
		assertGroupCount(messageTracker1, g0From1.getId(), 1, 0);

		db1.transaction(false, txn -> {
			socialBackupManager1.deleteAllMessages(txn, contactId0From1);
		});
		assertGroupCount(messageTracker1, g0From1.getId(), 0, 0);
	}

	private Collection<ConversationMessageHeader> getMessages1At0()
			throws DbException {
		return db0.transactionWithResult(true, txn -> socialBackupManager0
				.getMessageHeaders(txn, contactId1From0));
	}

	private Collection<ConversationMessageHeader> getMessages2At0()
			throws DbException {
		return db0.transactionWithResult(true, txn -> socialBackupManager0
				.getMessageHeaders(txn, contactId2From0));
	}

	private Collection<ConversationMessageHeader> getMessages0At1()
			throws DbException {
		return db1.transactionWithResult(true, txn -> socialBackupManager1
				.getMessageHeaders(txn, contactId0From1));
	}

	private Collection<ConversationMessageHeader> getMessages0At2()
			throws DbException {
		return db1.transactionWithResult(true, txn -> socialBackupManager2
				.getMessageHeaders(txn, contactId0From2));
	}

	public static void assertGroupCount(MessageTracker tracker, GroupId g,
			long msgCount, long unreadCount) throws DbException {
		MessageTracker.GroupCount c1 = tracker.getGroupCount(g);
		assertEquals(msgCount, c1.getMsgCount());
		assertEquals(unreadCount, c1.getUnreadCount());
	}

}
