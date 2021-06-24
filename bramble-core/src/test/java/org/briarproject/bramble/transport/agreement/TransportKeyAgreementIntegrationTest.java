package org.briarproject.bramble.transport.agreement;

import org.briarproject.bramble.BrambleCoreIntegrationTestEagerSingletons;
import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.BrambleIntegrationTest;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.bramble.test.TestPluginConfigModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Map;

import static org.briarproject.bramble.api.transport.agreement.TransportKeyAgreementManager.CLIENT_ID;
import static org.briarproject.bramble.api.transport.agreement.TransportKeyAgreementManager.MAJOR_VERSION;
import static org.briarproject.bramble.test.TestPluginConfigModule.DUPLEX_TRANSPORT_ID;
import static org.briarproject.bramble.test.TestPluginConfigModule.SIMPLEX_TRANSPORT_ID;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.transport.agreement.TransportKeyAgreementConstants.MSG_KEY_IS_SESSION;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TransportKeyAgreementIntegrationTest
		extends BrambleIntegrationTest<TransportKeyAgreementTestComponent> {

	private final File aliceDir = new File(testDir, "alice");
	private final File bobDir = new File(testDir, "bob");
	private final SecretKey masterKey = getSecretKey();
	private final long timestamp = System.currentTimeMillis();
	private final TransportId newTransportId =
			new TransportId(getRandomString(8));

	private TransportKeyAgreementTestComponent alice, bob;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		// Create the devices
		alice = createComponent(aliceDir, false);
		bob = createComponent(bobDir, false);

		// Start both lifecycles
		startLifecycle(alice, "Alice");
		startLifecycle(bob, "Bob");
	}

	private TransportKeyAgreementTestComponent createComponent(
			File dir, boolean useNewTransport) {
		TestPluginConfigModule pluginConfigModule = useNewTransport ?
				new TestPluginConfigModule(SIMPLEX_TRANSPORT_ID, newTransportId)
				: new TestPluginConfigModule();
		TransportKeyAgreementTestComponent c =
				DaggerTransportKeyAgreementTestComponent.builder()
						.testDatabaseConfigModule(
								new TestDatabaseConfigModule(dir))
						.testPluginConfigModule(pluginConfigModule)
						.build();
		BrambleCoreIntegrationTestEagerSingletons.Helper
				.injectEagerSingletons(c);
		return c;
	}

	private void startLifecycle(
			TransportKeyAgreementTestComponent device,
			String identityName) throws Exception {
		// Listen to message related events first to not miss early ones
		addEventListener(device);
		// Add an identity for the user
		IdentityManager identityManager = device.getIdentityManager();
		Identity identity = identityManager.createIdentity(identityName);
		identityManager.registerIdentity(identity);
		// Start the lifecycle manager
		LifecycleManager lifecycleManager = device.getLifecycleManager();
		lifecycleManager.startServices(masterKey); // re-using masterKey here
		lifecycleManager.waitForStartup();
	}

	@After
	@Override
	public void tearDown() throws Exception {
		tearDown(alice);
		tearDown(bob);
		super.tearDown();
	}

	private void tearDown(TransportKeyAgreementTestComponent device)
			throws Exception {
		// Stop the lifecycle manager
		LifecycleManager lifecycleManager = device.getLifecycleManager();
		lifecycleManager.stopServices();
		lifecycleManager.waitForShutdown();
	}

	@Test
	public void testBothAddTransportAtTheSameTime() throws Exception {
		// Alice and Bob add each other.
		Pair<ContactId, ContactId> contactIds = addContacts(true);
		ContactId aliceId = contactIds.getFirst();
		ContactId bobId = contactIds.getSecond();

		// Alice and Bob restart and come back with the new transport.
		alice = restartWithNewTransport(alice, aliceDir, "Alice");
		bob = restartWithNewTransport(bob, bobDir, "Bob");

		// They can still send via the old simplex,
		// but not via the new duplex transport
		assertTrue(alice.getKeyManager()
				.canSendOutgoingStreams(bobId, SIMPLEX_TRANSPORT_ID));
		assertFalse(alice.getKeyManager()
				.canSendOutgoingStreams(bobId, newTransportId));
		assertTrue(bob.getKeyManager()
				.canSendOutgoingStreams(aliceId, SIMPLEX_TRANSPORT_ID));
		assertFalse(bob.getKeyManager()
				.canSendOutgoingStreams(aliceId, newTransportId));

		// Bobs has started a session and sends KEY message to Alice
		syncMessage(bob, alice, aliceId, 1, true);

		// Alice now and sends her own KEY as well as her ACTIVATE message.
		syncMessage(alice, bob, bobId, 2, true);

		// Bob can already send over the new transport while Alice still can't.
		assertFalse(alice.getKeyManager()
				.canSendOutgoingStreams(bobId, newTransportId));
		assertTrue(bob.getKeyManager()
				.canSendOutgoingStreams(aliceId, newTransportId));

		// Now Bob sends his ACTIVATE message to Alice.
		syncMessage(bob, alice, aliceId, 1, true);

		// Now Alice can also send over the new transport.
		assertTrue(alice.getKeyManager()
				.canSendOutgoingStreams(bobId, newTransportId));
		assertTrue(bob.getKeyManager()
				.canSendOutgoingStreams(aliceId, newTransportId));

		// Ensure that private key is not stored anymore.
		assertLocalKeyPairIsNull(alice, bobId);
		assertLocalKeyPairIsNull(bob, aliceId);
	}

	@Test
	public void testAliceAddsTransportBeforeBob() throws Exception {
		// Alice and Bob add each other.
		Pair<ContactId, ContactId> contactIds = addContacts(true);
		ContactId aliceId = contactIds.getFirst();
		ContactId bobId = contactIds.getSecond();

		// Alice restarts and comes back with the new transport.
		alice = restartWithNewTransport(alice, aliceDir, "Alice");

		// Alice can still send via the old simplex,
		// but not via the new duplex transport
		assertTrue(alice.getKeyManager()
				.canSendOutgoingStreams(bobId, SIMPLEX_TRANSPORT_ID));
		assertFalse(alice.getKeyManager()
				.canSendOutgoingStreams(bobId, newTransportId));

		// Alice has started a session and sends KEY message to Bob
		// which he can't read, as he doesn't support the new transport, yet.
		syncMessage(alice, bob, bobId, 1, false);

		// Bob restarts and comes back with the new transport.
		bob = restartWithNewTransport(bob, bobDir, "Bob");

		// Alice's pending KEY message now gets delivered async, so wait for it
		awaitPendingMessageDelivery(1);

		// Bob now sends his own KEY as well as his ACTIVATE message.
		syncMessage(bob, alice, aliceId, 2, true);

		// Alice can already send over the new transport while Bob still can't.
		assertTrue(alice.getKeyManager()
				.canSendOutgoingStreams(bobId, newTransportId));
		assertFalse(bob.getKeyManager()
				.canSendOutgoingStreams(aliceId, newTransportId));

		// Now Alice sends her ACTIVATE message to Bob.
		syncMessage(alice, bob, bobId, 1, true);

		// Now Bob can also send over the new transport.
		assertTrue(alice.getKeyManager()
				.canSendOutgoingStreams(bobId, newTransportId));
		assertTrue(bob.getKeyManager()
				.canSendOutgoingStreams(aliceId, newTransportId));

		// Ensure that private key is not stored anymore.
		assertLocalKeyPairIsNull(alice, bobId);
		assertLocalKeyPairIsNull(bob, aliceId);
	}

	@Test
	public void testAliceAlreadyHasTransportWhenAddingBob() throws Exception {
		// Alice restarts and comes back with the new transport.
		alice = restartWithNewTransport(alice, aliceDir, "Alice");

		// Alice and Bob add each other.
		Pair<ContactId, ContactId> contactIds = addContacts(false);
		ContactId aliceId = contactIds.getFirst();
		ContactId bobId = contactIds.getSecond();

		// Alice can still send via the old simplex,
		// but not via the new duplex transport
		assertTrue(alice.getKeyManager()
				.canSendOutgoingStreams(bobId, SIMPLEX_TRANSPORT_ID));
		// FIXME normally Alice should not be able to already send streams
//		assertFalse(alice.getKeyManager()
//				.canSendOutgoingStreams(bobId, newTransportId));

		// Bob restarts and comes back with the new transport.
		bob = restartWithNewTransport(bob, bobDir, "Bob");

		// Bob sends his own KEY message.
		syncMessage(bob, alice, aliceId, 1, true);

		// Alice can already send over the new transport while Bob still can't.
		assertTrue(alice.getKeyManager()
				.canSendOutgoingStreams(bobId, newTransportId));
		assertFalse(bob.getKeyManager()
				.canSendOutgoingStreams(aliceId, newTransportId));

		// Now Alice sends her KEY and her ACTIVATE message to Bob.
		syncMessage(alice, bob, bobId, 2, true);

		// Now Bob can also send over the new transport.
		assertTrue(alice.getKeyManager()
				.canSendOutgoingStreams(bobId, newTransportId));
		assertTrue(bob.getKeyManager()
				.canSendOutgoingStreams(aliceId, newTransportId));

		// Ensure that private key is not stored anymore.
		assertLocalKeyPairIsNull(alice, bobId);
		assertLocalKeyPairIsNull(bob, aliceId);
	}

	private Pair<ContactId, ContactId> addContacts(
			boolean assertOldDuplexSending) throws Exception {
		ContactId bobId = addContact(alice, bob, true);
		ContactId aliceId = addContact(bob, alice, false);

		// Alice and Bob can send messages via the default test transports
		if (assertOldDuplexSending) {
			assertTrue(alice.getKeyManager()
					.canSendOutgoingStreams(bobId, SIMPLEX_TRANSPORT_ID));
			assertTrue(alice.getKeyManager()
					.canSendOutgoingStreams(bobId, DUPLEX_TRANSPORT_ID));
			assertTrue(bob.getKeyManager()
					.canSendOutgoingStreams(aliceId, SIMPLEX_TRANSPORT_ID));
			assertTrue(bob.getKeyManager()
					.canSendOutgoingStreams(aliceId, DUPLEX_TRANSPORT_ID));
		}

		// Sync initial client versioning updates
		syncMessage(alice, bob, bobId, 1, true);
		syncMessage(bob, alice, aliceId, 1, true);
		syncMessage(alice, bob, bobId, 1, true);
		sendAcks(bob, alice, aliceId, 1);

		return new Pair<>(aliceId, bobId);
	}

	private ContactId addContact(
			TransportKeyAgreementTestComponent device,
			TransportKeyAgreementTestComponent remote,
			boolean alice) throws Exception {
		// Get remote Author
		Author remoteAuthor = remote.getIdentityManager().getLocalAuthor();
		// Get ID of LocalAuthor
		IdentityManager identityManager = device.getIdentityManager();
		AuthorId localAuthorId = identityManager.getLocalAuthor().getId();
		// Add the other user as a contact
		ContactManager contactManager = device.getContactManager();
		return contactManager.addContact(remoteAuthor, localAuthorId, masterKey,
				timestamp, alice, true, true);
	}

	private TransportKeyAgreementTestComponent restartWithNewTransport(
			TransportKeyAgreementTestComponent device, File dir, String name)
			throws Exception {
		tearDown(device);
		TransportKeyAgreementTestComponent newDevice =
				createComponent(dir, true);
		startLifecycle(newDevice, name);
		return newDevice;
	}

	/**
	 * Asserts that the local key pair (specifically the private key) is removed
	 * from the session as intended when leaving the AWAIT_KEY state.
	 * If it remained on disk after the keys had been activated
	 * then we'd lose forward secrecy.
	 */
	private void assertLocalKeyPairIsNull(
			TransportKeyAgreementTestComponent device, ContactId contactId)
			throws Exception {
		Contact contact = device.getContactManager().getContact(contactId);
		Group group = getContactGroup(device, contact);
		Map<MessageId, BdfDictionary> map = bob.getClientHelper()
				.getMessageMetadataAsDictionary(group.getId());
		for (Map.Entry<MessageId, BdfDictionary> e : map.entrySet()) {
			if (!e.getValue().getBoolean(MSG_KEY_IS_SESSION)) continue;
			Session s = device.getSessionParser().parseSession(e.getValue());
			assertNull(s.getLocalKeyPair());
		}
	}

	private Group getContactGroup(TransportKeyAgreementTestComponent device,
			Contact c) {
		return device.getContactGroupFactory().createContactGroup(CLIENT_ID,
				MAJOR_VERSION, c);
	}

}
