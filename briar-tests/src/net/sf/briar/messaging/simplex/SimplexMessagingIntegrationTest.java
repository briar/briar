package net.sf.briar.messaging.simplex;

import static net.sf.briar.api.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.GROUP_SALT_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Random;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestDatabaseModule;
import net.sf.briar.TestLifecycleModule;
import net.sf.briar.TestUtils;
import net.sf.briar.api.Author;
import net.sf.briar.api.AuthorId;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.event.Event;
import net.sf.briar.api.event.EventListener;
import net.sf.briar.api.event.MessageAddedEvent;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageFactory;
import net.sf.briar.api.messaging.MessageVerifier;
import net.sf.briar.api.messaging.PacketReaderFactory;
import net.sf.briar.api.messaging.PacketWriterFactory;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.Endpoint;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.db.DatabaseModule;
import net.sf.briar.messaging.MessagingModule;
import net.sf.briar.messaging.duplex.DuplexMessagingModule;
import net.sf.briar.plugins.ImmediateExecutor;
import net.sf.briar.serial.SerialModule;
import net.sf.briar.system.ClockModule;
import net.sf.briar.transport.TransportModule;

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
	private final Group group;
	private final TransportId transportId;
	private final byte[] initialSecret;
	private final long epoch;

	private Injector alice, bob;

	public SimplexMessagingIntegrationTest() throws Exception {
		GroupId groupId = new GroupId(TestUtils.getRandomId());
		group = new Group(groupId, "Group", new byte[GROUP_SALT_LENGTH]);
		transportId = new TransportId(TestUtils.getRandomId());
		// Create matching secrets for Alice and Bob
		initialSecret = new byte[32];
		new Random().nextBytes(initialSecret);
		long rotationPeriod = 2 * CLOCK_DIFFERENCE + LATENCY;
		epoch = System.currentTimeMillis() - 2 * rotationPeriod;
	}

	@Before
	public void setUp() {
		testDir.mkdirs();
		alice = createInjector(aliceDir);
		bob = createInjector(bobDir);
	}

	private Injector createInjector(File dir) {
		return Guice.createInjector(new TestDatabaseModule(dir),
				new TestLifecycleModule(), new ClockModule(),
				new CryptoModule(), new DatabaseModule(), new MessagingModule(),
				new DuplexMessagingModule(), new SimplexMessagingModule(), 
				new SerialModule(), new TransportModule());
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
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[100]);
		db.addLocalAuthor(aliceAuthor);
		// Add Bob as a contact
		AuthorId bobId = new AuthorId(TestUtils.getRandomId());
		Author bobAuthor = new Author(bobId, "Bob",
				new byte[MAX_PUBLIC_KEY_LENGTH]);
		ContactId contactId = db.addContact(bobAuthor, aliceId);
		// Add the inbox group
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
		// Create an outgoing simplex connection
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionRegistry connRegistry =
				alice.getInstance(ConnectionRegistry.class);
		ConnectionWriterFactory connWriterFactory =
				alice.getInstance(ConnectionWriterFactory.class);
		PacketWriterFactory packetWriterFactory =
				alice.getInstance(PacketWriterFactory.class);
		TestSimplexTransportWriter transport = new TestSimplexTransportWriter(
				out, Long.MAX_VALUE, Long.MAX_VALUE, false);
		ConnectionContext ctx = km.getConnectionContext(contactId, transportId);
		assertNotNull(ctx);
		OutgoingSimplexConnection simplex = new OutgoingSimplexConnection(db,
				connRegistry, connWriterFactory, packetWriterFactory, ctx,
				transport);
		// Write whatever needs to be written
		simplex.write();
		assertTrue(transport.getDisposed());
		assertFalse(transport.getException());
		// Clean up
		km.stop();
		db.close();
		// Return the contents of the simplex connection
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
				new byte[MAX_PUBLIC_KEY_LENGTH], new byte[100]);
		db.addLocalAuthor(bobAuthor);
		// Add Alice as a contact
		AuthorId aliceId = new AuthorId(TestUtils.getRandomId());
		Author aliceAuthor = new Author(aliceId, "Alice",
				new byte[MAX_PUBLIC_KEY_LENGTH]);
		ContactId contactId = db.addContact(aliceAuthor, bobId);
		// Add the inbox group
		db.addGroup(group);
		db.setInboxGroup(contactId, group);
		// Add the transport and the endpoint
		db.addTransport(transportId, LATENCY);
		Endpoint ep = new Endpoint(contactId, transportId, epoch, false);
		db.addEndpoint(ep);
		km.endpointAdded(ep, LATENCY, initialSecret.clone());
		// Set up a database listener
		MessageListener listener = new MessageListener();
		db.addListener(listener);
		// Create a connection recogniser and recognise the connection
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		ConnectionRecogniser rec = bob.getInstance(ConnectionRecogniser.class);
		byte[] tag = new byte[TAG_LENGTH];
		int read = in.read(tag);
		assertEquals(tag.length, read);
		ConnectionContext ctx = rec.acceptConnection(transportId, tag);
		assertNotNull(ctx);
		// Create an incoming simplex connection
		MessageVerifier messageVerifier =
				bob.getInstance(MessageVerifier.class);
		ConnectionRegistry connRegistry =
				bob.getInstance(ConnectionRegistry.class);
		ConnectionReaderFactory connWriterFactory =
				bob.getInstance(ConnectionReaderFactory.class);
		PacketReaderFactory packetWriterFactory =
				bob.getInstance(PacketReaderFactory.class);
		TestSimplexTransportReader transport =
				new TestSimplexTransportReader(in);
		IncomingSimplexConnection simplex = new IncomingSimplexConnection(
				new ImmediateExecutor(), new ImmediateExecutor(),
				messageVerifier, db, connRegistry, connWriterFactory,
				packetWriterFactory, ctx, transport);
		// No messages should have been added yet
		assertFalse(listener.messageAdded);
		// Read whatever needs to be read
		simplex.read();
		assertTrue(transport.getDisposed());
		assertFalse(transport.getException());
		assertTrue(transport.getRecognised());
		// The private message from Alice should have been added
		assertTrue(listener.messageAdded);
		// Clean up
		km.stop();
		db.close();
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}

	private static class MessageListener implements EventListener {

		private boolean messageAdded = false;

		public void eventOccurred(Event e) {
			if(e instanceof MessageAddedEvent) messageAdded = true;
		}
	}
}
