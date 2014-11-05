package org.briarproject.messaging;

import static org.briarproject.api.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.messaging.MessagingConstants.GROUP_SALT_LENGTH;
import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Random;

import org.briarproject.BriarTestCase;
import org.briarproject.TestDatabaseModule;
import org.briarproject.TestLifecycleModule;
import org.briarproject.TestSystemModule;
import org.briarproject.TestUtils;
import org.briarproject.api.Author;
import org.briarproject.api.AuthorId;
import org.briarproject.api.ContactId;
import org.briarproject.api.LocalAuthor;
import org.briarproject.api.TransportId;
import org.briarproject.api.crypto.KeyManager;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageAddedEvent;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupFactory;
import org.briarproject.api.messaging.Message;
import org.briarproject.api.messaging.MessageFactory;
import org.briarproject.api.messaging.MessageVerifier;
import org.briarproject.api.messaging.MessagingSession;
import org.briarproject.api.messaging.PacketReaderFactory;
import org.briarproject.api.messaging.PacketWriterFactory;
import org.briarproject.api.transport.Endpoint;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamReader;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriter;
import org.briarproject.api.transport.StreamWriterFactory;
import org.briarproject.api.transport.TagRecogniser;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.event.EventModule;
import org.briarproject.plugins.ImmediateExecutor;
import org.briarproject.serial.SerialModule;
import org.briarproject.transport.TransportModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class SimplexMessagingIntegrationTest extends BriarTestCase {

	private static final long CLOCK_DIFFERENCE = 60 * 1000;
	private static final long LATENCY = 60 * 1000;

	private final File testDir = TestUtils.getTestDirectory();
	private final File aliceDir = new File(testDir, "alice");
	private final File bobDir = new File(testDir, "bob");
	private final TransportId transportId;
	private final byte[] initialSecret;
	private final long epoch;

	private Injector alice, bob;

	public SimplexMessagingIntegrationTest() throws Exception {
		transportId = new TransportId("id");
		// Create matching secrets for Alice and Bob
		initialSecret = new byte[32];
		new Random().nextBytes(initialSecret);
		long rotationPeriod = 2 * CLOCK_DIFFERENCE + LATENCY;
		epoch = System.currentTimeMillis() - 2 * rotationPeriod;
	}

	@Override
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
				new MessagingModule(), new SerialModule(),
				new TransportModule());
	}

	@Test
	public void testInjection() {
		DatabaseComponent aliceDb = alice.getInstance(DatabaseComponent.class);
		DatabaseComponent bobDb = bob.getInstance(DatabaseComponent.class);
		assertFalse(aliceDb == bobDb);
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
		KeyManager km = alice.getInstance(KeyManager.class);
		km.start();
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
		// Add the transport and the endpoint
		db.addTransport(transportId, LATENCY);
		Endpoint ep = new Endpoint(contactId, transportId, epoch, true);
		db.addEndpoint(ep);
		km.endpointAdded(ep, LATENCY, initialSecret.clone());
		// Send Bob a message
		String contentType = "text/plain";
		long timestamp = System.currentTimeMillis();
		byte[] body = "Hi Bob!".getBytes("UTF-8");
		MessageFactory messageFactory = alice.getInstance(MessageFactory.class);
		Message message = messageFactory.createAnonymousMessage(null, group,
				contentType, timestamp, body);
		db.addLocalMessage(message);
		// Create a stream writer
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamWriterFactory streamWriterFactory =
				alice.getInstance(StreamWriterFactory.class);
		StreamContext ctx = km.getStreamContext(contactId, transportId);
		assertNotNull(ctx);
		StreamWriter streamWriter = streamWriterFactory.createStreamWriter(out,
				MAX_FRAME_LENGTH, ctx);
		// Create an outgoing messaging session
		PacketWriterFactory packetWriterFactory =
				alice.getInstance(PacketWriterFactory.class);
		MessagingSession session = new SinglePassOutgoingSession(db,
				new ImmediateExecutor(), packetWriterFactory, contactId,
				Long.MAX_VALUE, streamWriter.getOutputStream());
		// Write whatever needs to be written
		session.run();
		// Clean up
		km.stop();
		db.close();
		// Return the contents of the stream
		return out.toByteArray();
	}

	private void read(byte[] b) throws Exception {
		// Open Bob's database
		DatabaseComponent db = bob.getInstance(DatabaseComponent.class);
		assertFalse(db.open());
		// Start Bob's key manager
		KeyManager km = bob.getInstance(KeyManager.class);
		km.start();
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
		// Add the transport and the endpoint
		db.addTransport(transportId, LATENCY);
		Endpoint ep = new Endpoint(contactId, transportId, epoch, false);
		db.addEndpoint(ep);
		km.endpointAdded(ep, LATENCY, initialSecret.clone());
		// Set up an event listener
		MessageListener listener = new MessageListener();
		bob.getInstance(EventBus.class).addListener(listener);
		// Create a tag recogniser and recognise the tag
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		TagRecogniser rec = bob.getInstance(TagRecogniser.class);
		byte[] tag = new byte[TAG_LENGTH];
		int read = in.read(tag);
		assertEquals(tag.length, read);
		StreamContext ctx = rec.recogniseTag(transportId, tag);
		assertNotNull(ctx);
		// Create a stream reader
		StreamReaderFactory streamReaderFactory =
				bob.getInstance(StreamReaderFactory.class);
		StreamReader streamReader = streamReaderFactory.createStreamReader(in,
				MAX_FRAME_LENGTH, ctx);
		// Create an incoming messaging session
		MessageVerifier messageVerifier =
				bob.getInstance(MessageVerifier.class);
		PacketReaderFactory packetReaderFactory =
				bob.getInstance(PacketReaderFactory.class);
		MessagingSession session = new IncomingSession(db,
				new ImmediateExecutor(), new ImmediateExecutor(),
				messageVerifier, packetReaderFactory, contactId,
				streamReader.getInputStream());
		// No messages should have been added yet
		assertFalse(listener.messageAdded);
		// Read whatever needs to be read
		session.run();
		// The private message from Alice should have been added
		assertTrue(listener.messageAdded);
		// Clean up
		km.stop();
		db.close();
	}

	@Override
	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}

	private static class MessageListener implements EventListener {

		private volatile boolean messageAdded = false;

		public void eventOccurred(Event e) {
			if(e instanceof MessageAddedEvent) messageAdded = true;
		}
	}
}
