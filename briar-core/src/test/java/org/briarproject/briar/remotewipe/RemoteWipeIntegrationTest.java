package org.briarproject.briar.remotewipe;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.remotewipe.RemoteWipeManager;
import org.briarproject.briar.api.remotewipe.RemoteWipeMessageHeader;
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

public class RemoteWipeIntegrationTest extends BriarIntegrationTest<BriarIntegrationTestComponent> {

	private RemoteWipeManager remoteWipeManager0;
	private RemoteWipeManager remoteWipeManager1;
	private RemoteWipeManager remoteWipeManager2;

	private Group g1From0;
	private Group g0From1;
	private Group g2From0;
	private Group g0From2;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		remoteWipeManager0 = c0.getRemoteWipeManager();
		remoteWipeManager1 = c1.getRemoteWipeManager();
		remoteWipeManager2 = c2.getRemoteWipeManager();

		g1From0 = remoteWipeManager0.getContactGroup(contact1From0);
		g0From1 = remoteWipeManager1.getContactGroup(contact0From1);
		g2From0 = remoteWipeManager0.getContactGroup(contact2From0);
		g0From2 = remoteWipeManager2.getContactGroup(contact0From2);
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
	public void testRemoteWipe() throws Exception {
		db0.transaction(false, txn -> {
			// TODO assert that we do not already have a wipe setup
//			assertNull(socialBackupManager0.getBackupMetadata(txn));
			remoteWipeManager0.setup(txn,
					asList(contactId1From0, contactId2From0));
			// TODO now check that we do have a wipe setup
		});
		// Sync the setup messages to the contacts
		sync0To1(1, true);
		sync0To2(1, true);

		// The setup message from 0 should have arrived at 1
		Collection<ConversationMessageHeader> messages0At1 =
				getMessages0At1();
		assertEquals(1, messages0At1.size());

		Collection<ConversationMessageHeader> messages0At2 =
				getMessages0At2();
		assertEquals(1, messages0At2.size());

		for (ConversationMessageHeader h : messages0At1) {
			assertTrue(h instanceof RemoteWipeMessageHeader);
			RemoteWipeMessageHeader r = (RemoteWipeMessageHeader) h;
			assertFalse(r.isLocal());
		}

		// The wipers check that they are now wipers
		db1.transaction(false, txn -> {
			assertTrue(remoteWipeManager1.amWiper(txn, contactId0From1));
			remoteWipeManager1.wipe(txn, contact0From1);
		});

		db2.transaction(false, txn -> {
			assertTrue(remoteWipeManager2.amWiper(txn, contactId0From2));
			remoteWipeManager2.wipe(txn, contact0From2);
		});

		// Sync the wipe messages to the wipee
		sync1To0(1, true);
		sync2To0(1, true);

		Collection<ConversationMessageHeader> messages1At0 =
				getMessages1At0();
		assertEquals(1, messages1At0.size());

		Collection<ConversationMessageHeader> messages2At0 =
				getMessages2At0();
		assertEquals(1, messages2At0.size());
	}

	private Collection<ConversationMessageHeader> getMessages1At0()
			throws DbException {
		return db0.transactionWithResult(true, txn -> remoteWipeManager0
				.getMessageHeaders(txn, contactId1From0));
	}

	private Collection<ConversationMessageHeader> getMessages2At0()
			throws DbException {
		return db0.transactionWithResult(true, txn -> remoteWipeManager0
				.getMessageHeaders(txn, contactId2From0));
	}

	private Collection<ConversationMessageHeader> getMessages0At1()
			throws DbException {
		return db1.transactionWithResult(true, txn -> remoteWipeManager1
				.getMessageHeaders(txn, contactId0From1));
	}

	private Collection<ConversationMessageHeader> getMessages0At2()
			throws DbException {
		return db1.transactionWithResult(true, txn -> remoteWipeManager2
				.getMessageHeaders(txn, contactId0From2));
	}

	public static void assertGroupCount(MessageTracker tracker, GroupId g,
			long msgCount, long unreadCount) throws DbException {
		MessageTracker.GroupCount c1 = tracker.getGroupCount(g);
		assertEquals(msgCount, c1.getMsgCount());
		assertEquals(unreadCount, c1.getUnreadCount());
	}
}
