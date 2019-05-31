package org.briarproject.bramble.contact;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.bramble.test.TestDuplexTransportConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static junit.framework.TestCase.fail;
import static org.briarproject.bramble.test.TestDuplexTransportConnection.createPair;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ContactExchangeIntegrationTest extends BrambleTestCase {

	private static final int TIMEOUT = 15_000;

	private final File testDir = getTestDirectory();
	private final File aliceDir = new File(testDir, "alice");
	private final File bobDir = new File(testDir, "bob");
	private final SecretKey masterKey = getSecretKey();

	private ContactExchangeIntegrationTestComponent alice, bob;
	private Author aliceAuthor, bobAuthor;

	@Before
	public void setUp() throws Exception {
		assertTrue(testDir.mkdirs());
		// Create the devices
		alice = DaggerContactExchangeIntegrationTestComponent.builder()
				.testDatabaseConfigModule(
						new TestDatabaseConfigModule(aliceDir)).build();
		alice.injectBrambleCoreEagerSingletons();
		bob = DaggerContactExchangeIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(bobDir))
				.build();
		bob.injectBrambleCoreEagerSingletons();
		// Set up the devices and get the identities
		aliceAuthor = setUp(alice, "Alice");
		bobAuthor = setUp(bob, "Bob");
	}

	private Author setUp(ContactExchangeIntegrationTestComponent device,
			String name) throws Exception {
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
		return identity.getLocalAuthor();
	}

	@Test
	public void testExchangeContacts() throws Exception {
		TestDuplexTransportConnection[] pair = createPair();
		TestDuplexTransportConnection aliceConnection = pair[0];
		TestDuplexTransportConnection bobConnection = pair[1];
		CountDownLatch aliceFinished = new CountDownLatch(1);
		alice.getIoExecutor().execute(() -> {
			try {
				alice.getContactExchangeManager().exchangeContacts(
						aliceConnection, masterKey, true, true);
				aliceFinished.countDown();
			} catch (Exception e) {
				fail();
			}
		});
		CountDownLatch bobFinished = new CountDownLatch(1);
		bob.getIoExecutor().execute(() -> {
			try {
				bob.getContactExchangeManager().exchangeContacts(bobConnection,
						masterKey, false, true);
				bobFinished.countDown();
			} catch (Exception e) {
				fail();
			}
		});
		aliceFinished.await(TIMEOUT, MILLISECONDS);
		bobFinished.await(TIMEOUT, MILLISECONDS);
		assertContactsExchanged();
	}

	private void assertContactsExchanged() throws Exception {
		Collection<Contact> aliceContacts =
				alice.getContactManager().getContacts();
		assertEquals(1, aliceContacts.size());
		Contact bobFromAlice = aliceContacts.iterator().next();
		assertEquals(bobAuthor, bobFromAlice.getAuthor());
		Collection<Contact> bobContacts = bob.getContactManager().getContacts();
		assertEquals(1, bobContacts.size());
		Contact aliceFromBob = bobContacts.iterator().next();
		assertEquals(aliceAuthor, aliceFromBob.getAuthor());
	}

	private void tearDown(ContactExchangeIntegrationTestComponent device)
			throws Exception {
		// Stop the lifecycle manager
		LifecycleManager lifecycleManager = device.getLifecycleManager();
		lifecycleManager.stopServices();
		lifecycleManager.waitForShutdown();
	}

	@After
	public void tearDown() throws Exception {
		tearDown(alice);
		tearDown(bob);
		deleteTestDirectory(testDir);
	}
}
