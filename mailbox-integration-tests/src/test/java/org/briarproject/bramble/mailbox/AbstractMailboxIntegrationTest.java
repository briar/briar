package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbCallable;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.mailbox.MailboxAuthToken;
import org.briarproject.bramble.api.mailbox.MailboxPairingState;
import org.briarproject.bramble.api.mailbox.MailboxPairingTask;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxUpdateWithMailbox;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.test.BrambleIntegrationTest;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.mailbox.lib.AbstractMailbox;
import org.briarproject.mailbox.lib.TestMailbox;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.briarproject.bramble.api.mailbox.MailboxAuthToken.fromString;
import static org.briarproject.bramble.mailbox.MailboxIntegrationTestComponent.Helper.injectEagerSingletons;
import static org.briarproject.bramble.mailbox.MailboxIntegrationTestUtils.createMailboxApi;
import static org.briarproject.bramble.mailbox.MailboxTestUtils.getQrCodePayload;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

abstract class AbstractMailboxIntegrationTest
		extends BrambleIntegrationTest<MailboxIntegrationTestComponent> {

	static final String URL_BASE = "http://127.0.0.1:8000";

	private final File dir1 = new File(testDir, "alice");
	private final File dir2 = new File(testDir, "bob");
	private final SecretKey rootKey = getSecretKey();

	MailboxIntegrationTestComponent c1, c2;
	Contact contact1From2, contact2From1;
	TestMailbox mailbox;
	MailboxApi api = createMailboxApi();

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		c1 = startTestComponent(dir1, "Alice");
		c2 = startTestComponent(dir2, "Bob");
		mailbox = new TestMailbox(new File(testDir, "mailbox"));
		mailbox.startLifecycle();
	}

	@After
	@Override
	public void tearDown() throws Exception {
		super.tearDown();
		c1.getLifecycleManager().stopServices();
		c2.getLifecycleManager().stopServices();
		c1.getLifecycleManager().waitForShutdown();
		c2.getLifecycleManager().waitForShutdown();
		mailbox.stopLifecycle(true);
	}

	MailboxIntegrationTestComponent startTestComponent(
			File databaseDir, String name) throws Exception {
		TestDatabaseConfigModule dbModule =
				new TestDatabaseConfigModule(databaseDir);
		MailboxIntegrationTestComponent component =
				DaggerMailboxIntegrationTestComponent
						.builder()
						.testDatabaseConfigModule(dbModule)
						.build();
		injectEagerSingletons(component);
		component.getPluginManager().setPluginEnabled(TorConstants.ID, false);

		setUp(component, name);
		return component;
	}

	private void setUp(MailboxIntegrationTestComponent device,
			String name) throws Exception {
		// Add an identity for the user
		IdentityManager identityManager = device.getIdentityManager();
		Identity identity = identityManager.createIdentity(name);
		identityManager.registerIdentity(identity);
		// Start the lifecycle manager
		LifecycleManager lifecycleManager = device.getLifecycleManager();
		lifecycleManager.startServices(getSecretKey());
		lifecycleManager.waitForStartup();
		addEventListener(device);
	}

	MailboxProperties pair(MailboxIntegrationTestComponent c,
			AbstractMailbox mailbox) throws Exception {
		MailboxAuthToken setupToken = fromString(mailbox.getSetupToken());

		MailboxPairingTask pairingTask = c.getMailboxManager()
				.startPairingTask(getQrCodePayload(setupToken.getBytes()));

		CountDownLatch latch = new CountDownLatch(1);
		pairingTask.addObserver((state) -> {
			if (state instanceof MailboxPairingState.Paired) {
				latch.countDown();
			}
		});
		if (!latch.await(10, SECONDS)) {
			fail("Timeout reached when waiting for pairing.");
		}
		return c.getDatabaseComponent()
				.transactionWithNullableResult(true, txn ->
						c.getMailboxSettingsManager()
								.getOwnMailboxProperties(txn)
				);
	}

	void addContacts() throws Exception {
		LocalAuthor author1 = c1.getIdentityManager().getLocalAuthor();
		LocalAuthor author2 = c2.getIdentityManager().getLocalAuthor();

		ContactId contactId1 = c1.getContactManager().addContact(author2,
				author1.getId(), rootKey, c1.getClock().currentTimeMillis(),
				true, true, true);
		ContactId contactId2 = c2.getContactManager().addContact(author1,
				author2.getId(), rootKey, c2.getClock().currentTimeMillis(),
				false, true, true);

		contact2From1 = c1.getContactManager().getContact(contactId2);
		contact1From2 = c2.getContactManager().getContact(contactId1);

		// Sync client versioning update from 0 to 1
		sync1To2(1, true);
		// Sync client versioning update and ack from 1 to 0
		sync2To1(1, true);
		// Sync second client versioning update, mailbox properties and ack
		// from 0 to 1
		sync1To2(2, true);
		// Sync mailbox properties and ack from 1 to 0
		sync2To1(1, true);
		// Sync final ack from 0 to 1
		ack1To2(1);
	}

	<T> T getFromDb(MailboxIntegrationTestComponent device,
			DbCallable<T, ?> callable) throws Exception {
		return device.getDatabaseComponent()
				.transactionWithResult(true, callable::call);
	}

	void restartTor(MailboxIntegrationTestComponent device)
			throws PluginException {
		Plugin torPlugin = device.getPluginManager().getPlugin(TorConstants.ID);
		assertNotNull(torPlugin);
		torPlugin.stop();
		torPlugin.start();
	}

	MailboxProperties getMailboxProperties(
			MailboxIntegrationTestComponent device, ContactId contactId)
			throws DbException {
		DatabaseComponent db = device.getDatabaseComponent();
		MailboxUpdateWithMailbox update = (MailboxUpdateWithMailbox)
				db.transactionWithNullableResult(true, txn ->
						device.getMailboxUpdateManager()
								.getRemoteUpdate(txn, contactId)
				);
		if (update == null) fail();
		return update.getMailboxProperties();
	}

	void sendMessage(MailboxIntegrationTestComponent from,
			ContactId toContactId, String text) throws Exception {
		GroupId g = from.getMessagingManager().getConversationId(toContactId);
		PrivateMessage m = from.getPrivateMessageFactory().createPrivateMessage(
				g, from.getClock().currentTimeMillis(), text, emptyList(),
				NO_AUTO_DELETE_TIMER);
		from.getMessagingManager().addLocalMessage(m);
	}

	void sync1To2(int num, boolean valid) throws Exception {
		syncMessage(c1, c2, contact2From1.getId(), num, valid);
	}

	void sync2To1(int num, boolean valid) throws Exception {
		syncMessage(c2, c1, contact1From2.getId(), num, valid);
	}

	void ack1To2(int num) throws Exception {
		sendAcks(c1, c2, contact2From1.getId(), num);
	}

	void ack2To1(int num) throws Exception {
		sendAcks(c2, c1, contact1From2.getId(), num);
	}

}
