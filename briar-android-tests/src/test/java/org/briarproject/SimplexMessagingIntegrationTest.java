package org.briarproject;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateMessage;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.SyncSession;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import static org.briarproject.TestPluginsModule.MAX_LATENCY;
import static org.briarproject.TestPluginsModule.TRANSPORT_ID;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SimplexMessagingIntegrationTest extends BriarTestCase {

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
		bob = DaggerSimplexMessagingIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(bobDir)).build();
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
		lifecycleManager.startServices();
		lifecycleManager.waitForStartup();
		// Add an identity for Alice
		LocalAuthor aliceAuthor = new LocalAuthor(aliceId, "Alice",
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[123], timestamp);
		identityManager.addLocalAuthor(aliceAuthor);
		// Add Bob as a contact
		Author bobAuthor = new Author(bobId, "Bob",
				new byte[MAX_PUBLIC_KEY_LENGTH]);
		ContactId contactId = contactManager.addContact(bobAuthor, aliceId,
				master, timestamp, true, true);

		// Send Bob a message
		GroupId groupId = messagingManager.getConversationId(contactId);
		byte[] body = "Hi Bob!".getBytes("UTF-8");
		PrivateMessage message = privateMessageFactory.createPrivateMessage(
				groupId, timestamp, null, "text/plain", body);
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
		// Bob needs a MessagingManager even though we're not using it directly
		bob.getMessagingManager();

		// Start the lifecyle manager
		lifecycleManager.startServices();
		lifecycleManager.waitForStartup();
		// Add an identity for Bob
		LocalAuthor bobAuthor = new LocalAuthor(bobId, "Bob",
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[123], timestamp);
		identityManager.addLocalAuthor(bobAuthor);
		// Add Alice as a contact
		Author aliceAuthor = new Author(aliceId, "Alice",
				new byte[MAX_PUBLIC_KEY_LENGTH]);
		ContactId contactId = contactManager.addContact(aliceAuthor, bobId,
				master, timestamp, false, true);

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

	private static class MessageListener implements EventListener {

		private volatile boolean messageAdded = false;

		public void eventOccurred(Event e) {
			if (e instanceof MessageAddedEvent) messageAdded = true;
		}
	}
}
