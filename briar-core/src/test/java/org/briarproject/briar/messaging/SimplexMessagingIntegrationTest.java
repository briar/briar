package org.briarproject.briar.messaging;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.SyncSession;
import org.briarproject.bramble.api.sync.SyncSessionFactory;
import org.briarproject.bramble.api.sync.event.MessageAddedEvent;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.api.transport.StreamContext;
import org.briarproject.bramble.api.transport.StreamReaderFactory;
import org.briarproject.bramble.api.transport.StreamWriterFactory;
import org.briarproject.bramble.system.SystemModule;
import org.briarproject.bramble.test.TestDatabaseModule;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessage;
import org.briarproject.briar.api.messaging.PrivateMessageFactory;
import org.briarproject.briar.test.BriarTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;
import static org.briarproject.bramble.test.TestPluginConfigModule.MAX_LATENCY;
import static org.briarproject.bramble.test.TestPluginConfigModule.TRANSPORT_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SimplexMessagingIntegrationTest extends BriarTestCase {

	private final static String ALICE = "Alice";
	private final static String BOB = "Bob";

	private final File testDir = TestUtils.getTestDirectory();
	private final File aliceDir = new File(testDir, "alice");
	private final File bobDir = new File(testDir, "bob");
	private final SecretKey master = TestUtils.getSecretKey();
	private final long timestamp = System.currentTimeMillis();
	private final AuthorId aliceId = new AuthorId(TestUtils.getRandomId());
	private final AuthorId bobId = new AuthorId(TestUtils.getRandomId());

	private SimplexMessagingIntegrationTestComponent alice, bob;

	@Before
	public void setUp() {
		assertTrue(testDir.mkdirs());
		alice = DaggerSimplexMessagingIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(aliceDir)).build();
		injectEagerSingletons(alice);
		alice.inject(new SystemModule.EagerSingletons());
		bob = DaggerSimplexMessagingIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(bobDir)).build();
		injectEagerSingletons(bob);
	}

	@Test
	public void testWriteAndRead() throws Exception {
		read(write());
	}


	private byte[] write() throws Exception {
		// Instantiate Alice's services
		LifecycleManager lifecycleManager = alice.getLifecycleManager();
		IdentityManager identityManager = alice.getIdentityManager();
		ContactManager contactManager = alice.getContactManager();
		MessagingManager messagingManager = alice.getMessagingManager();
		KeyManager keyManager = alice.getKeyManager();
		PrivateMessageFactory privateMessageFactory =
				alice.getPrivateMessageFactory();
		StreamWriterFactory streamWriterFactory =
				alice.getStreamWriterFactory();
		SyncSessionFactory syncSessionFactory = alice.getSyncSessionFactory();

		// Start the lifecycle manager
		lifecycleManager.startServices(null);
		lifecycleManager.waitForStartup();
		// Add an identity for Alice
		LocalAuthor aliceAuthor = new LocalAuthor(aliceId, "Alice",
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[123], timestamp);
		identityManager.registerLocalAuthor(aliceAuthor);
		// Add Bob as a contact
		Author bobAuthor = new Author(bobId, BOB,
				new byte[MAX_PUBLIC_KEY_LENGTH]);
		ContactId contactId = contactManager.addContact(bobAuthor,
				aliceAuthor.getId(), master, timestamp, true, true, true);

		// Send Bob a message
		GroupId groupId = messagingManager.getConversationId(contactId);
		String body = "Hi Bob!";
		PrivateMessage message = privateMessageFactory.createPrivateMessage(
				groupId, timestamp, body);
		messagingManager.addLocalMessage(message);
		// Get a stream context
		StreamContext ctx = keyManager.getStreamContext(contactId,
				TRANSPORT_ID);
		assertNotNull(ctx);
		// Create a stream writer
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		OutputStream streamWriter = streamWriterFactory.createStreamWriter(
				out, ctx);
		// Create an outgoing sync session
		SyncSession session = syncSessionFactory.createSimplexOutgoingSession(
				contactId, MAX_LATENCY, streamWriter);
		// Write whatever needs to be written
		session.run();
		streamWriter.close();

		// Clean up
		lifecycleManager.stopServices();
		lifecycleManager.waitForShutdown();

		// Return the contents of the stream
		return out.toByteArray();
	}

	private void read(byte[] stream) throws Exception {
		// Instantiate Bob's services
		LifecycleManager lifecycleManager = bob.getLifecycleManager();
		IdentityManager identityManager = bob.getIdentityManager();
		ContactManager contactManager = bob.getContactManager();
		KeyManager keyManager = bob.getKeyManager();
		StreamReaderFactory streamReaderFactory = bob.getStreamReaderFactory();
		SyncSessionFactory syncSessionFactory = bob.getSyncSessionFactory();

		// Start the lifecyle manager
		lifecycleManager.startServices(null);
		lifecycleManager.waitForStartup();
		// Add an identity for Bob
		LocalAuthor bobAuthor = new LocalAuthor(bobId, BOB,
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[123], timestamp);
		identityManager.registerLocalAuthor(bobAuthor);
		// Add Alice as a contact
		Author aliceAuthor = new Author(aliceId, ALICE,
				new byte[MAX_PUBLIC_KEY_LENGTH]);
		ContactId contactId = contactManager.addContact(aliceAuthor,
				bobAuthor.getId(), master, timestamp, false, true, true);
		// Set up an event listener
		MessageListener listener = new MessageListener();
		bob.getEventBus().addListener(listener);
		// Read and recognise the tag
		ByteArrayInputStream in = new ByteArrayInputStream(stream);
		byte[] tag = new byte[TAG_LENGTH];
		int read = in.read(tag);
		assertEquals(tag.length, read);
		StreamContext ctx = keyManager.getStreamContext(TRANSPORT_ID, tag);
		assertNotNull(ctx);
		// Create a stream reader
		InputStream streamReader = streamReaderFactory.createStreamReader(
				in, ctx);
		// Create an incoming sync session
		SyncSession session = syncSessionFactory.createIncomingSession(
				contactId, streamReader);
		// No messages should have been added yet
		assertFalse(listener.messageAdded);
		// Read whatever needs to be read
		session.run();
		streamReader.close();
		// The private message from Alice should have been added
		assertTrue(listener.messageAdded);

		// Clean up
		lifecycleManager.stopServices();
		lifecycleManager.waitForShutdown();
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}

	private static void injectEagerSingletons(
			SimplexMessagingIntegrationTestComponent component) {
		component.inject(new MessagingModule.EagerSingletons());
		component.inject(new SystemModule.EagerSingletons());
	}

	@NotNullByDefault
	private static class MessageListener implements EventListener {

		private volatile boolean messageAdded = false;

		@Override
		public void eventOccurred(Event e) {
			if (e instanceof MessageAddedEvent) messageAdded = true;
		}
	}
}
