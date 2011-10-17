package net.sf.briar.transport.batch;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;

import junit.framework.TestCase;
import net.sf.briar.TestDatabaseModule;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.event.DatabaseEvent;
import net.sf.briar.api.db.event.DatabaseListener;
import net.sf.briar.api.db.event.MessagesAddedEvent;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageEncoder;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.writers.ProtocolWriterFactory;
import net.sf.briar.api.transport.BatchTransportReader;
import net.sf.briar.api.transport.BatchTransportWriter;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.ConnectionRecogniserFactory;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.db.DatabaseModule;
import net.sf.briar.protocol.ProtocolModule;
import net.sf.briar.protocol.writers.ProtocolWritersModule;
import net.sf.briar.serial.SerialModule;
import net.sf.briar.transport.TransportModule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class BatchConnectionReadWriteTest extends TestCase {

	private final File testDir = TestUtils.getTestDirectory();
	private final File aliceDir = new File(testDir, "alice");
	private final File bobDir = new File(testDir, "bob");
	private final TransportId transportId = new TransportId(123);
	private final Map<TransportId, TransportProperties> transports =
		Collections.emptyMap();
	private final byte[] aliceSecret, bobSecret;

	private Injector alice, bob;

	public BatchConnectionReadWriteTest() throws Exception {
		super();
		// Create matching secrets for Alice and Bob
		aliceSecret = new byte[100];
		aliceSecret[16] = (byte) 1;
		bobSecret = new byte[100];
	}

	@Before
	public void setUp() {
		testDir.mkdirs();
		// Create Alice's injector
		alice = Guice.createInjector(new CryptoModule(), new DatabaseModule(),
				new ProtocolModule(), new ProtocolWritersModule(),
				new SerialModule(), new TestDatabaseModule(aliceDir),
				new TransportModule());
		// Create Bob's injector
		bob = Guice.createInjector(new CryptoModule(), new DatabaseModule(),
				new ProtocolModule(), new ProtocolWritersModule(),
				new SerialModule(), new TestDatabaseModule(bobDir),
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
		db.open(false);
		// Add Bob as a contact and send him a message
		ContactId contactId = db.addContact(transports, aliceSecret);
		byte[] messageBody = "Hi Bob!".getBytes("UTF-8");
		MessageEncoder encoder = alice.getInstance(MessageEncoder.class);
		Message message = encoder.encodeMessage(null, messageBody);
		db.addLocalPrivateMessage(message, contactId);
		// Create an outgoing batch connection
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionWriterFactory connFactory =
			alice.getInstance(ConnectionWriterFactory.class);
		ProtocolWriterFactory protoFactory =
			alice.getInstance(ProtocolWriterFactory.class);
		BatchTransportWriter writer = new TestBatchTransportWriter(out);
		OutgoingBatchConnection batchOut = new OutgoingBatchConnection(
				connFactory, db, protoFactory, transportId, contactId, writer);
		// Write whatever needs to be written
		batchOut.write();
		// Close Alice's database
		db.close();
		// Return the contents of the batch connection
		return out.toByteArray();
	}

	private void read(byte[] b) throws Exception {
		// Open Bob's database
		DatabaseComponent db = bob.getInstance(DatabaseComponent.class);
		db.open(false);
		// Set up a database listener
		MessageListener listener = new MessageListener();
		db.addListener(listener);
		// Add Alice as a contact
		ContactId contactId = db.addContact(transports, bobSecret);
		// Create a connection recogniser and recognise the connection
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		ConnectionRecogniserFactory recFactory =
			bob.getInstance(ConnectionRecogniserFactory.class);
		ConnectionRecogniser rec =
			recFactory.createConnectionRecogniser(transportId);
		byte[] encryptedIv = new byte[IV_LENGTH];
		int read = in.read(encryptedIv);
		assertEquals(encryptedIv.length, read);
		ContactId accepted = rec.acceptConnection(encryptedIv);
		assertEquals(contactId, accepted);
		// Create an incoming batch connection
		ConnectionReaderFactory connFactory =
			bob.getInstance(ConnectionReaderFactory.class);
		ProtocolReaderFactory protoFactory =
			bob.getInstance(ProtocolReaderFactory.class);
		BatchTransportReader reader = new TestBatchTransportReader(in);
		IncomingBatchConnection batchIn = new IncomingBatchConnection(
				connFactory, db, protoFactory, contactId, reader, encryptedIv);
		// No messages should have been added yet
		assertFalse(listener.messagesAdded);
		// Read whatever needs to be read
		batchIn.read();
		// The private message from Alice should have been added
		assertTrue(listener.messagesAdded);
		// Close Bob's database
		db.close();
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}

	private static class MessageListener implements DatabaseListener {

		private boolean messagesAdded = false;

		public void eventOccurred(DatabaseEvent e) {
			if(e instanceof MessagesAddedEvent) messagesAdded = true;
		}
	}

	private static class TestBatchTransportWriter
	implements BatchTransportWriter {

		private final OutputStream out;

		private TestBatchTransportWriter(OutputStream out) {
			this.out = out;
		}

		public long getCapacity() {
			return Long.MAX_VALUE;
		}

		public OutputStream getOutputStream() {
			return out;
		}

		public void dispose(boolean success) {
			assertTrue(success);
		}
	}

	private static class TestBatchTransportReader
	implements BatchTransportReader {

		private final InputStream in;

		private TestBatchTransportReader(InputStream in) {
			this.in = in;
		}

		public InputStream getInputStream() {
			return in;
		}

		public void dispose(boolean success) {
			assertTrue(success);
		}
	}
}
