package org.briarproject.sync;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.briarproject.BriarTestCase;
import org.briarproject.TestDatabaseModule;
import org.briarproject.TestLifecycleModule;
import org.briarproject.TestSystemModule;
import org.briarproject.TestUtils;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.MessageVerifier;
import org.briarproject.api.sync.MessagingSession;
import org.briarproject.api.sync.PacketReader;
import org.briarproject.api.sync.PacketReaderFactory;
import org.briarproject.api.sync.PacketWriter;
import org.briarproject.api.sync.PacketWriterFactory;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;
import org.briarproject.api.transport.TransportKeys;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.data.DataModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.event.EventModule;
import org.briarproject.plugins.ImmediateExecutor;
import org.briarproject.transport.TransportModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.sync.MessagingConstants.GROUP_SALT_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_CLOCK_DIFFERENCE;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SimplexMessagingIntegrationTest extends BriarTestCase {

	private static final int MAX_LATENCY = 2 * 60 * 1000; // 2 minutes
	private static final long ROTATION_PERIOD_LENGTH =
			MAX_LATENCY + MAX_CLOCK_DIFFERENCE;

	private final File testDir = TestUtils.getTestDirectory();
	private final File aliceDir = new File(testDir, "alice");
	private final File bobDir = new File(testDir, "bob");
	private final TransportId transportId = new TransportId("id");
	private final SecretKey master = TestUtils.createSecretKey();
	private final long timestamp = System.currentTimeMillis();

	private Injector alice, bob;

	@Before
	public void setUp() {
		testDir.mkdirs();
		alice = createInjector(aliceDir);
		bob = createInjector(bobDir);
	}

	private Injector createInjector(File dir) {
		return Guice.createInjector(new TestDatabaseModule(dir),
				new TestLifecycleModule(), new TestSystemModule(),
				new CryptoModule(), new DatabaseModule(), new EventModule(),
				new SyncModule(), new DataModule(),
				new TransportModule());
	}

	@Test
	public void testWriteAndRead() throws Exception {
		read(write());
	}

	private byte[] write() throws Exception {
		// Open Alice's database
		DatabaseComponent db = alice.getInstance(DatabaseComponent.class);
		assertFalse(db.open());
		// Start Alice's key manager
		KeyManager keyManager = alice.getInstance(KeyManager.class);
		keyManager.start();
		// Add a local pseudonym for Alice
		AuthorId aliceId = new AuthorId(TestUtils.getRandomId());
		LocalAuthor aliceAuthor = new LocalAuthor(aliceId, "Alice",
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[100], 1234);
		db.addLocalAuthor(aliceAuthor);
		// Add Bob as a contact
		AuthorId bobId = new AuthorId(TestUtils.getRandomId());
		Author bobAuthor = new Author(bobId, "Bob",
				new byte[MAX_PUBLIC_KEY_LENGTH]);
		ContactId contactId = db.addContact(bobAuthor, aliceId);
		// Add the inbox group
		GroupFactory gf = alice.getInstance(GroupFactory.class);
		Group group = gf.createGroup("Group", new byte[GROUP_SALT_LENGTH]);
		db.addGroup(group);
		db.setInboxGroup(contactId, group);
		// Add the transport
		db.addTransport(transportId, MAX_LATENCY);
		// Derive and store the transport keys
		long rotationPeriod = timestamp / ROTATION_PERIOD_LENGTH;
		CryptoComponent crypto = alice.getInstance(CryptoComponent.class);
		TransportKeys keys = crypto.deriveTransportKeys(transportId, master,
				rotationPeriod, true);
		db.addTransportKeys(contactId, keys);
		keyManager.contactAdded(contactId, Collections.singletonList(keys));
		// Send Bob a message
		String contentType = "text/plain";
		byte[] body = "Hi Bob!".getBytes("UTF-8");
		MessageFactory messageFactory = alice.getInstance(MessageFactory.class);
		Message message = messageFactory.createAnonymousMessage(null, group,
				contentType, timestamp, body);
		db.addLocalMessage(message);
		// Get a stream context
		StreamContext ctx = keyManager.getStreamContext(contactId, transportId);
		assertNotNull(ctx);
		// Create a stream writer
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamWriterFactory streamWriterFactory =
				alice.getInstance(StreamWriterFactory.class);
		OutputStream streamWriter =
				streamWriterFactory.createStreamWriter(out, ctx);
		// Create an outgoing messaging session
		EventBus eventBus = alice.getInstance(EventBus.class);
		PacketWriterFactory packetWriterFactory =
				alice.getInstance(PacketWriterFactory.class);
		PacketWriter packetWriter = packetWriterFactory.createPacketWriter(
				streamWriter);
		MessagingSession session = new org.briarproject.sync.SimplexOutgoingSession(db,
				new ImmediateExecutor(), eventBus, contactId, transportId,
				MAX_LATENCY, packetWriter);
		// Write whatever needs to be written
		session.run();
		streamWriter.close();
		// Clean up
		keyManager.stop();
		db.close();
		// Return the contents of the stream
		return out.toByteArray();
	}

	private void read(byte[] stream) throws Exception {
		// Open Bob's database
		DatabaseComponent db = bob.getInstance(DatabaseComponent.class);
		assertFalse(db.open());
		// Start Bob's key manager
		KeyManager keyManager = bob.getInstance(KeyManager.class);
		keyManager.start();
		// Add a local pseudonym for Bob
		AuthorId bobId = new AuthorId(TestUtils.getRandomId());
		LocalAuthor bobAuthor = new LocalAuthor(bobId, "Bob",
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[100], 1234);
		db.addLocalAuthor(bobAuthor);
		// Add Alice as a contact
		AuthorId aliceId = new AuthorId(TestUtils.getRandomId());
		Author aliceAuthor = new Author(aliceId, "Alice",
				new byte[MAX_PUBLIC_KEY_LENGTH]);
		ContactId contactId = db.addContact(aliceAuthor, bobId);
		// Add the inbox group
		GroupFactory gf = bob.getInstance(GroupFactory.class);
		Group group = gf.createGroup("Group", new byte[GROUP_SALT_LENGTH]);
		db.addGroup(group);
		db.setInboxGroup(contactId, group);
		// Add the transport
		db.addTransport(transportId, MAX_LATENCY);
		// Derive and store the transport keys
		long rotationPeriod = timestamp / ROTATION_PERIOD_LENGTH;
		CryptoComponent crypto = bob.getInstance(CryptoComponent.class);
		TransportKeys keys = crypto.deriveTransportKeys(transportId, master,
				rotationPeriod, false);
		db.addTransportKeys(contactId, keys);
		keyManager.contactAdded(contactId, Collections.singletonList(keys));
		// Set up an event listener
		MessageListener listener = new MessageListener();
		bob.getInstance(EventBus.class).addListener(listener);
		// Read and recognise the tag
		ByteArrayInputStream in = new ByteArrayInputStream(stream);
		byte[] tag = new byte[TAG_LENGTH];
		int read = in.read(tag);
		assertEquals(tag.length, read);
		StreamContext ctx = keyManager.recogniseTag(transportId, tag);
		assertNotNull(ctx);
		// Create a stream reader
		StreamReaderFactory streamReaderFactory =
				bob.getInstance(StreamReaderFactory.class);
		InputStream streamReader =
				streamReaderFactory.createStreamReader(in, ctx);
		// Create an incoming messaging session
		EventBus eventBus = bob.getInstance(EventBus.class);
		MessageVerifier messageVerifier =
				bob.getInstance(MessageVerifier.class);
		PacketReaderFactory packetReaderFactory =
				bob.getInstance(PacketReaderFactory.class);
		PacketReader packetReader = packetReaderFactory.createPacketReader(
				streamReader);
		MessagingSession session = new org.briarproject.sync.IncomingSession(db,
				new ImmediateExecutor(), new ImmediateExecutor(), eventBus,
				messageVerifier, contactId, transportId, packetReader);
		// No messages should have been added yet
		assertFalse(listener.messageAdded);
		// Read whatever needs to be read
		session.run();
		streamReader.close();
		// The private message from Alice should have been added
		assertTrue(listener.messageAdded);
		// Clean up
		keyManager.stop();
		db.close();
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
