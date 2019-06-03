package org.briarproject.briar.messaging;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.SyncSession;
import org.briarproject.bramble.api.sync.SyncSessionFactory;
import org.briarproject.bramble.api.sync.event.MessageStateChangedEvent;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.api.transport.StreamContext;
import org.briarproject.bramble.api.transport.StreamReaderFactory;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.bramble.api.transport.StreamWriterFactory;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.briar.api.messaging.PrivateMessageFactory;
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent;
import org.briarproject.briar.test.BriarTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.briarproject.bramble.api.sync.validation.MessageState.DELIVERED;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;
import static org.briarproject.bramble.test.TestPluginConfigModule.MAX_LATENCY;
import static org.briarproject.bramble.test.TestPluginConfigModule.TRANSPORT_ID;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SimplexMessagingIntegrationTest extends BriarTestCase {

	private static final int TIMEOUT_MS = 5_000;

	private final File testDir = getTestDirectory();
	private final File aliceDir = new File(testDir, "alice");
	private final File bobDir = new File(testDir, "bob");
	private final SecretKey rootKey = getSecretKey();
	private final long timestamp = System.currentTimeMillis();

	private SimplexMessagingIntegrationTestComponent alice, bob;

	@Before
	public void setUp() {
		assertTrue(testDir.mkdirs());
		alice = DaggerSimplexMessagingIntegrationTestComponent.builder()
				.testDatabaseConfigModule(
						new TestDatabaseConfigModule(aliceDir)).build();
		alice.injectSimplexMessagingEagerSingletons();
		bob = DaggerSimplexMessagingIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(bobDir))
				.build();
		bob.injectSimplexMessagingEagerSingletons();
	}

	@Test
	public void testWriteAndRead() throws Exception {
		// Create the identities
		Identity aliceIdentity =
				alice.getIdentityManager().createIdentity("Alice");
		Identity bobIdentity = bob.getIdentityManager().createIdentity("Bob");
		// Set up the devices and get the contact IDs
		ContactId bobId = setUp(alice, aliceIdentity,
				bobIdentity.getLocalAuthor(), true);
		ContactId aliceId = setUp(bob, bobIdentity,
				aliceIdentity.getLocalAuthor(), false);
		// Add a private message listener
		PrivateMessageListener listener = new PrivateMessageListener();
		bob.getEventBus().addListener(listener);
		// Alice sends a private message to Bob
		sendMessage(alice, bobId);
		// Sync Alice's client versions and transport properties
		read(bob, aliceId, write(alice, bobId), 2);
		// Sync Bob's client versions and transport properties
		read(alice, bobId, write(bob, aliceId), 2);
		// Sync the private message
		read(bob, aliceId, write(alice, bobId), 1);
		// Bob should have received the private message
		assertTrue(listener.messageAdded);
	}

	private ContactId setUp(SimplexMessagingIntegrationTestComponent device,
			Identity local, Author remote, boolean alice) throws Exception {
		// Add an identity for the user
		IdentityManager identityManager = device.getIdentityManager();
		identityManager.registerIdentity(local);
		// Start the lifecycle manager
		LifecycleManager lifecycleManager = device.getLifecycleManager();
		lifecycleManager.startServices(getSecretKey());
		lifecycleManager.waitForStartup();
		// Add the other user as a contact
		ContactManager contactManager = device.getContactManager();
		return contactManager.addContact(remote, local.getId(), rootKey,
				timestamp, alice, true, true);
	}

	private void sendMessage(SimplexMessagingIntegrationTestComponent device,
			ContactId contactId) throws Exception {
		// Send Bob a message
		MessagingManager messagingManager = device.getMessagingManager();
		GroupId groupId = messagingManager.getConversationId(contactId);
		PrivateMessageFactory privateMessageFactory =
				device.getPrivateMessageFactory();
		PrivateMessage message = privateMessageFactory.createPrivateMessage(
				groupId, System.currentTimeMillis(), "Hi!", emptyList());
		messagingManager.addLocalMessage(message);
	}

	private void read(SimplexMessagingIntegrationTestComponent device,
			ContactId contactId, byte[] stream, int deliveries)
			throws Exception {
		// Listen for message deliveries
		MessageDeliveryListener listener =
				new MessageDeliveryListener(deliveries);
		device.getEventBus().addListener(listener);
		// Read and recognise the tag
		ByteArrayInputStream in = new ByteArrayInputStream(stream);
		byte[] tag = new byte[TAG_LENGTH];
		int read = in.read(tag);
		assertEquals(tag.length, read);
		KeyManager keyManager = device.getKeyManager();
		StreamContext ctx = keyManager.getStreamContext(TRANSPORT_ID, tag);
		assertNotNull(ctx);
		// Create a stream reader
		StreamReaderFactory streamReaderFactory =
				device.getStreamReaderFactory();
		InputStream streamReader = streamReaderFactory.createStreamReader(
				in, ctx);
		// Create an incoming sync session
		SyncSessionFactory syncSessionFactory = device.getSyncSessionFactory();
		SyncSession session = syncSessionFactory.createIncomingSession(
				contactId, streamReader);
		// Read whatever needs to be read
		session.run();
		streamReader.close();
		// Wait for the messages to be delivered
		assertTrue(listener.delivered.await(TIMEOUT_MS, MILLISECONDS));
		// Clean up the listener
		device.getEventBus().removeListener(listener);
	}

	private byte[] write(SimplexMessagingIntegrationTestComponent device,
			ContactId contactId) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Get a stream context
		KeyManager keyManager = device.getKeyManager();
		StreamContext ctx = keyManager.getStreamContext(contactId,
				TRANSPORT_ID);
		assertNotNull(ctx);
		// Create a stream writer
		StreamWriterFactory streamWriterFactory =
				device.getStreamWriterFactory();
		StreamWriter streamWriter =
				streamWriterFactory.createStreamWriter(out, ctx);
		// Create an outgoing sync session
		SyncSessionFactory syncSessionFactory = device.getSyncSessionFactory();
		SyncSession session = syncSessionFactory.createSimplexOutgoingSession(
				contactId, MAX_LATENCY, streamWriter);
		// Write whatever needs to be written
		session.run();
		streamWriter.sendEndOfStream();
		// Return the contents of the stream
		return out.toByteArray();
	}

	private void tearDown(SimplexMessagingIntegrationTestComponent device)
			throws Exception {
		// Stop the lifecycle manager
		LifecycleManager lifecycleManager = device.getLifecycleManager();
		lifecycleManager.stopServices();
		lifecycleManager.waitForShutdown();
	}

	@After
	public void tearDown() throws Exception {
		// Tear down the devices
		tearDown(alice);
		tearDown(bob);
		deleteTestDirectory(testDir);
	}

	@NotNullByDefault
	private static class MessageDeliveryListener implements EventListener {

		private final CountDownLatch delivered;

		private MessageDeliveryListener(int deliveries) {
			delivered = new CountDownLatch(deliveries);
		}

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof MessageStateChangedEvent) {
				MessageStateChangedEvent m = (MessageStateChangedEvent) e;
				if (m.getState().equals(DELIVERED)) delivered.countDown();
			}
		}
	}

	@NotNullByDefault
	private static class PrivateMessageListener implements EventListener {

		private volatile boolean messageAdded = false;

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof PrivateMessageReceivedEvent) messageAdded = true;
		}
	}
}
