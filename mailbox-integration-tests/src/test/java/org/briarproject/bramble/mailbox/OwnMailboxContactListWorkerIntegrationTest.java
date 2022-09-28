package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.mailbox.MailboxAuthToken;
import org.briarproject.bramble.api.mailbox.MailboxPairingState.Paired;
import org.briarproject.bramble.api.mailbox.MailboxPairingTask;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.mailbox.MailboxApi.ApiException;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.mailbox.lib.Mailbox;
import org.briarproject.mailbox.lib.TestMailbox;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.mailbox.MailboxAuthToken.fromString;
import static org.briarproject.bramble.mailbox.MailboxIntegrationTestUtils.createMailboxApi;
import static org.briarproject.bramble.mailbox.MailboxIntegrationTestUtils.createTestComponent;
import static org.briarproject.bramble.mailbox.MailboxIntegrationTestUtils.getQrCodePayload;
import static org.briarproject.bramble.mailbox.MailboxIntegrationTestUtils.retryUntilSuccessOrTimeout;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class OwnMailboxContactListWorkerIntegrationTest
		extends BrambleTestCase {

	private static final Logger LOG = getLogger(
			OwnMailboxContactListWorkerIntegrationTest.class.getName());

	@Rule
	public TemporaryFolder mailboxDataDirectory = new TemporaryFolder();

	private Mailbox mailbox;

	private final MailboxApi api = createMailboxApi();

	private MailboxProperties ownerProperties;

	private final File testDir = getTestDirectory();
	private final File aliceDir = new File(testDir, "alice");

	private MailboxIntegrationTestComponent component;
	private Identity identity;

	private final SecretKey rootKey = getSecretKey();
	private final long timestamp = System.currentTimeMillis();

	@Before
	public void setUp() throws Exception {
		mailbox = new TestMailbox(mailboxDataDirectory.getRoot());
		mailbox.init();
		mailbox.startLifecycle();

		MailboxAuthToken setupToken = fromString(mailbox.getSetupToken());

		component = createTestComponent(aliceDir);
		identity = setUp(component, "Alice");

		MailboxPairingTask pairingTask = component.getMailboxManager()
				.startPairingTask(getQrCodePayload(setupToken));

		CountDownLatch latch = new CountDownLatch(1);
		pairingTask.addObserver((state) -> {
			if (state instanceof Paired) {
				latch.countDown();
			}
		});
		latch.await();

		ownerProperties = component.getDatabaseComponent()
				.transactionWithNullableResult(false, txn ->
						component.getMailboxSettingsManager()
								.getOwnMailboxProperties(txn)
				);
	}

	@After
	public void tearDown() {
		mailbox.stopLifecycle(true);
	}

	private Identity setUp(MailboxIntegrationTestComponent device, String name)
			throws Exception {
		// Add an identity for the user
		IdentityManager identityManager = device.getIdentityManager();
		Identity identity = identityManager.createIdentity(name);
		identityManager.registerIdentity(identity);
		// Start the lifecycle manager
		LifecycleManager lifecycleManager = device.getLifecycleManager();
		lifecycleManager.startServices(getSecretKey());
		lifecycleManager.waitForStartup();
		// Check the initial conditions
		ContactManager contactManager = device.getContactManager();
		assertEquals(0, contactManager.getPendingContacts().size());
		assertEquals(0, contactManager.getContacts().size());
		return identity;
	}

	@Test
	public void testUploadContacts() throws DbException {
		int numContactsToAdd = 5;
		List<ContactId> expectedContacts =
				createContacts(component, identity, numContactsToAdd);

		// Check for number of contacts on mailbox via API every 100ms
		retryUntilSuccessOrTimeout(1000, 100, () -> {
			try {
				Collection<ContactId> contacts =
						api.getContacts(ownerProperties);
				if (contacts.size() == numContactsToAdd) {
					assertEquals(expectedContacts, contacts);
					return true;
				}
			} catch (IOException | ApiException e) {
				LOG.log(WARNING, "Error while fetching contacts via API", e);
				fail();
			}
			return false;
		});
	}

	private List<ContactId> createContacts(
			MailboxIntegrationTestComponent component, Identity local,
			int numContacts) throws DbException {
		List<ContactId> contactIds = new ArrayList<>();
		ContactManager contactManager = component.getContactManager();
		AuthorFactory authorFactory = component.getAuthorFactory();
		for (int i = 0; i < numContacts; i++) {
			Author remote = authorFactory.createLocalAuthor("Bob " + i);
			contactIds.add(contactManager.addContact(remote, local.getId(),
					rootKey, timestamp, true, true, true));
		}
		return contactIds;
	}
}
