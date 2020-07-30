package org.briarproject.bramble.contact;

import org.briarproject.bramble.BrambleCoreIntegrationTestEagerSingletons;
import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.contact.PendingContactState;
import org.briarproject.bramble.api.contact.event.ContactAddedEvent;
import org.briarproject.bramble.api.contact.event.PendingContactStateChangedEvent;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.bramble.test.TestDuplexTransportConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.fail;
import static org.briarproject.bramble.api.contact.PendingContactState.OFFLINE;
import static org.briarproject.bramble.test.TestDuplexTransportConnection.createPair;
import static org.briarproject.bramble.test.TestPluginConfigModule.DUPLEX_TRANSPORT_ID;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ContactExchangeIntegrationTest extends BrambleTestCase {

	private static final int TIMEOUT = 15_000;

	private final File testDir = getTestDirectory();
	private final File aliceDir = new File(testDir, "alice");
	private final File bobDir = new File(testDir, "bob");
	private final SecretKey masterKey = getSecretKey();
	private final Random random = new Random();

	private ContactExchangeIntegrationTestComponent alice, bob;
	private Identity aliceIdentity, bobIdentity;

	@Before
	public void setUp() throws Exception {
		assertTrue(testDir.mkdirs());
		// Create the devices
		alice = DaggerContactExchangeIntegrationTestComponent.builder()
				.testDatabaseConfigModule(
						new TestDatabaseConfigModule(aliceDir)).build();
		BrambleCoreIntegrationTestEagerSingletons.Helper
				.injectEagerSingletons(alice);
		bob = DaggerContactExchangeIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(bobDir))
				.build();
		BrambleCoreIntegrationTestEagerSingletons.Helper
				.injectEagerSingletons(bob);
		// Set up the devices and get the identities
		aliceIdentity = setUp(alice, "Alice");
		bobIdentity = setUp(bob, "Bob");
	}

	private Identity setUp(ContactExchangeIntegrationTestComponent device,
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
		return identity;
	}

	@Test
	public void testExchangeContacts() throws Exception {
		TestDuplexTransportConnection[] pair = createPair();
		TestDuplexTransportConnection aliceConnection = pair[0];
		TestDuplexTransportConnection bobConnection = pair[1];
		CountDownLatch aliceFinished = new CountDownLatch(1);
		CountDownLatch bobFinished = new CountDownLatch(1);
		boolean verified = random.nextBoolean();

		alice.getIoExecutor().execute(() -> {
			try {
				alice.getContactExchangeManager().exchangeContacts(
						aliceConnection, masterKey, true, verified);
				aliceFinished.countDown();
			} catch (Exception e) {
				fail();
			}
		});
		bob.getIoExecutor().execute(() -> {
			try {
				bob.getContactExchangeManager().exchangeContacts(bobConnection,
						masterKey, false, verified);
				bobFinished.countDown();
			} catch (Exception e) {
				fail();
			}
		});
		assertTrue(aliceFinished.await(TIMEOUT, MILLISECONDS));
		assertTrue(bobFinished.await(TIMEOUT, MILLISECONDS));
		assertContacts(verified, false);
		assertNoPendingContacts();
	}

	@Test
	public void testExchangeContactsFromPendingContacts() throws Exception {
		PendingContact bobFromAlice = addPendingContact(alice, bob);
		PendingContact aliceFromBob = addPendingContact(bob, alice);
		assertPendingContacts();

		TestDuplexTransportConnection[] pair = createPair();
		TestDuplexTransportConnection aliceConnection = pair[0];
		TestDuplexTransportConnection bobConnection = pair[1];
		CountDownLatch aliceFinished = new CountDownLatch(1);
		CountDownLatch bobFinished = new CountDownLatch(1);
		boolean verified = random.nextBoolean();

		alice.getIoExecutor().execute(() -> {
			try {
				alice.getContactExchangeManager().exchangeContacts(
						bobFromAlice.getId(), aliceConnection, masterKey, true,
						verified);
				aliceFinished.countDown();
			} catch (Exception e) {
				fail();
			}
		});
		bob.getIoExecutor().execute(() -> {
			try {
				bob.getContactExchangeManager().exchangeContacts(
						aliceFromBob.getId(), bobConnection, masterKey, false,
						verified);
				bobFinished.countDown();
			} catch (Exception e) {
				fail();
			}
		});
		assertTrue(aliceFinished.await(TIMEOUT, MILLISECONDS));
		assertTrue(bobFinished.await(TIMEOUT, MILLISECONDS));
		assertContacts(verified, true);
		assertNoPendingContacts();
	}

	@Test
	public void testHandshakeAndExchangeContactsFromPendingContacts()
			throws Exception {
		PendingContact bobFromAlice = addPendingContact(alice, bob);
		PendingContact aliceFromBob = addPendingContact(bob, alice);
		assertPendingContacts();

		TestDuplexTransportConnection[] pair = createPair();
		TestDuplexTransportConnection aliceConnection = pair[0];
		TestDuplexTransportConnection bobConnection = pair[1];
		CountDownLatch aliceFinished = new CountDownLatch(1);
		CountDownLatch bobFinished = new CountDownLatch(1);

		alice.getEventBus().addListener(e -> {
			if (e instanceof ContactAddedEvent) aliceFinished.countDown();
		});
		alice.getConnectionManager().manageOutgoingConnection(
				bobFromAlice.getId(), DUPLEX_TRANSPORT_ID, aliceConnection);
		bob.getEventBus().addListener(e -> {
			if (e instanceof ContactAddedEvent) bobFinished.countDown();
		});
		bob.getConnectionManager().manageIncomingConnection(
				aliceFromBob.getId(), DUPLEX_TRANSPORT_ID, bobConnection);
		assertTrue(aliceFinished.await(TIMEOUT, MILLISECONDS));
		assertTrue(bobFinished.await(TIMEOUT, MILLISECONDS));
		assertContacts(false, true);
		assertNoPendingContacts();
	}

	private PendingContact addPendingContact(
			ContactExchangeIntegrationTestComponent local,
			ContactExchangeIntegrationTestComponent remote) throws Exception {
		EventWaiter waiter = new EventWaiter();
		local.getEventBus().addListener(waiter);
		String link = remote.getContactManager().getHandshakeLink();
		String alias = remote.getIdentityManager().getLocalAuthor().getName();
		PendingContact pendingContact =
				local.getContactManager().addPendingContact(link, alias);
		waiter.latch.await(TIMEOUT, MILLISECONDS);
		return pendingContact;
	}

	private void assertContacts(boolean verified,
			boolean withHandshakeKeys) throws Exception {
		assertContact(alice, bobIdentity, verified, withHandshakeKeys);
		assertContact(bob, aliceIdentity, verified, withHandshakeKeys);
	}

	private void assertContact(ContactExchangeIntegrationTestComponent local,
			Identity expectedIdentity, boolean verified,
			boolean withHandshakeKey) throws Exception {
		Collection<Contact> contacts = local.getContactManager().getContacts();
		assertEquals(1, contacts.size());
		Contact contact = contacts.iterator().next();
		assertEquals(expectedIdentity.getLocalAuthor(), contact.getAuthor());
		assertEquals(verified, contact.isVerified());
		PublicKey expectedPublicKey = expectedIdentity.getHandshakePublicKey();
		PublicKey actualPublicKey = contact.getHandshakePublicKey();
		assertNotNull(expectedPublicKey);
		if (withHandshakeKey) {
			assertNotNull(actualPublicKey);
			assertArrayEquals(expectedPublicKey.getEncoded(),
					actualPublicKey.getEncoded());
		} else {
			assertNull(actualPublicKey);
		}
	}

	private void assertNoPendingContacts() throws Exception {
		assertEquals(0, alice.getContactManager().getPendingContacts().size());
		assertEquals(0, bob.getContactManager().getPendingContacts().size());
	}

	private void assertPendingContacts() throws Exception {
		assertPendingContact(alice, bobIdentity);
		assertPendingContact(bob, aliceIdentity);
	}

	private void assertPendingContact(
			ContactExchangeIntegrationTestComponent local,
			Identity expectedIdentity) throws Exception {
		Collection<Pair<PendingContact, PendingContactState>> pairs =
				local.getContactManager().getPendingContacts();
		assertEquals(1, pairs.size());
		Pair<PendingContact, PendingContactState> pair =
				pairs.iterator().next();
		assertEquals(OFFLINE, pair.getSecond());
		PendingContact pendingContact = pair.getFirst();
		assertEquals(expectedIdentity.getLocalAuthor().getName(),
				pendingContact.getAlias());
		PublicKey expectedPublicKey = expectedIdentity.getHandshakePublicKey();
		assertNotNull(expectedPublicKey);
		assertArrayEquals(expectedPublicKey.getEncoded(),
				pendingContact.getPublicKey().getEncoded());
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

	@NotNullByDefault
	private static class EventWaiter implements EventListener {

		private final CountDownLatch latch = new CountDownLatch(1);

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof PendingContactStateChangedEvent) {
				PendingContactStateChangedEvent p =
						(PendingContactStateChangedEvent) e;
				if (p.getPendingContactState() == OFFLINE) latch.countDown();
			}
		}
	}
}
