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
import org.briarproject.bramble.api.mailbox.MailboxPairingState.Paired;
import org.briarproject.bramble.api.mailbox.MailboxPairingTask;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxUpdateWithMailbox;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.test.BrambleIntegrationTest;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.bramble.test.TestLogFormatter;
import org.briarproject.bramble.test.TestThreadFactoryModule;
import org.briarproject.mailbox.lib.AbstractMailbox;
import org.briarproject.mailbox.lib.TestMailbox;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.IntSupplier;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.briarproject.bramble.api.mailbox.MailboxAuthToken.fromString;
import static org.briarproject.bramble.mailbox.MailboxIntegrationTestComponent.Helper.injectEagerSingletons;
import static org.briarproject.bramble.mailbox.MailboxIntegrationTestUtils.createMailboxApi;
import static org.briarproject.bramble.mailbox.MailboxTestUtils.getQrCodePayload;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

abstract class AbstractMailboxIntegrationTest
		extends BrambleIntegrationTest<MailboxIntegrationTestComponent> {

	static final String URL_BASE = "http://127.0.0.1";

	AbstractMailboxIntegrationTest() {
		TestLogFormatter.use();
	}

	private final TransportId transportId = new TransportId(getRandomString(4));
	private final File dir1 = new File(testDir, "alice");
	private final File dir2 = new File(testDir, "bob");
	private final SecretKey rootKey = getSecretKey();

	MailboxIntegrationTestComponent c1, c2;
	Contact contact1From2, contact2From1;
	TestMailbox mailbox;
	MailboxApi api;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		mailbox = new TestMailbox(new File(testDir, "mailbox"));
		c1 = startTestComponent(dir1, "Alice", () -> mailbox.getPort());
		c2 = startTestComponent(dir2, "Bob", () -> mailbox.getPort());
		api = createMailboxApi(() -> mailbox.getPort());
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

	private MailboxIntegrationTestComponent startTestComponent(
			File databaseDir, String name, IntSupplier portSupplier)
			throws Exception {
		TestThreadFactoryModule threadFactoryModule =
				new TestThreadFactoryModule(name);
		TestDatabaseConfigModule dbModule =
				new TestDatabaseConfigModule(databaseDir);
		TestModularMailboxModule mailboxModule =
				new TestModularMailboxModule(portSupplier);
		MailboxIntegrationTestComponent component =
				DaggerMailboxIntegrationTestComponent
						.builder()
						.testThreadFactoryModule(threadFactoryModule)
						.testDatabaseConfigModule(dbModule)
						.testModularMailboxModule(mailboxModule)
						.build();
		injectEagerSingletons(component);

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
			if (state instanceof Paired) {
				latch.countDown();
			}
		});
		if (!latch.await(10, SECONDS)) {
			fail("Timeout reached when waiting for pairing.");
		}
		MailboxProperties properties = c.getDatabaseComponent()
				.transactionWithNullableResult(true, txn ->
						c.getMailboxSettingsManager()
								.getOwnMailboxProperties(txn)
				);
		assertNotNull(properties);
		return properties;
	}

	void addContacts() throws Exception {
		LocalAuthor author1 = c1.getIdentityManager().getLocalAuthor();
		LocalAuthor author2 = c2.getIdentityManager().getLocalAuthor();

		ContactId contactId2From1 =
				c1.getContactManager().addContact(author2,
						author1.getId(), rootKey,
						c1.getClock().currentTimeMillis(),
						true, true, true);
		ContactId contactId1From2 =
				c2.getContactManager().addContact(author1,
						author2.getId(), rootKey,
						c2.getClock().currentTimeMillis(),
						false, true, true);

		contact2From1 = c1.getContactManager().getContact(contactId2From1);
		contact1From2 = c2.getContactManager().getContact(contactId1From2);

		// Sync client versioning update from 1 to 2
		sync1To2(1, true);
		// Sync client versioning update and ack from 2 to 1
		sync2To1(1, true);
		// Sync second client versioning update, mailbox properties and ack
		// from 1 to 2
		sync1To2(2, true);
		// Sync mailbox properties and ack from 2 to 1
		sync2To1(1, true);
		// Sync final ack from 1 to 2
		ack1To2(1);
	}

	<T> T getFromDb(MailboxIntegrationTestComponent device,
			DbCallable<T, ?> callable) throws Exception {
		return device.getDatabaseComponent()
				.transactionWithResult(true, callable::call);
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

	void broadcastMessage(MailboxIntegrationTestComponent from)
			throws Exception {
		TransportProperties p = from.getTransportPropertyManager()
				.getLocalProperties(transportId);
		p.put(getRandomString(23), getRandomString(8));
		from.getTransportPropertyManager().mergeLocalProperties(transportId, p);
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

	void assertNumMessages(MailboxIntegrationTestComponent c,
			ContactId contactId, int num) throws DbException {
		Map<ContactId, TransportProperties> p = c.getTransportPropertyManager()
				.getRemoteProperties(transportId);
		assertEquals(num, p.get(contactId).size());
	}

}
