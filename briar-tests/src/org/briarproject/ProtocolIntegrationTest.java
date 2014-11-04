package org.briarproject;

import static org.briarproject.api.transport.TransportConstants.MAX_FRAME_LENGTH;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Random;

import org.briarproject.api.Author;
import org.briarproject.api.AuthorFactory;
import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.messaging.Ack;
import org.briarproject.api.messaging.Group;
import org.briarproject.api.messaging.GroupFactory;
import org.briarproject.api.messaging.Message;
import org.briarproject.api.messaging.MessageFactory;
import org.briarproject.api.messaging.MessageId;
import org.briarproject.api.messaging.MessageVerifier;
import org.briarproject.api.messaging.Offer;
import org.briarproject.api.messaging.PacketReader;
import org.briarproject.api.messaging.PacketReaderFactory;
import org.briarproject.api.messaging.PacketWriter;
import org.briarproject.api.messaging.PacketWriterFactory;
import org.briarproject.api.messaging.Request;
import org.briarproject.api.messaging.SubscriptionUpdate;
import org.briarproject.api.messaging.TransportUpdate;
import org.briarproject.api.messaging.UnverifiedMessage;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamReader;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriter;
import org.briarproject.api.transport.StreamWriterFactory;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.event.EventModule;
import org.briarproject.messaging.MessagingModule;
import org.briarproject.reliability.ReliabilityModule;
import org.briarproject.serial.SerialModule;
import org.briarproject.transport.TransportModule;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ProtocolIntegrationTest extends BriarTestCase {

	private final StreamReaderFactory streamReaderFactory;
	private final StreamWriterFactory streamWriterFactory;
	private final PacketReaderFactory packetReaderFactory;
	private final PacketWriterFactory packetWriterFactory;
	private final MessageVerifier messageVerifier;

	private final ContactId contactId;
	private final byte[] secret;
	private final Author author;
	private final Group group;
	private final Message message, message1;
	private final String authorName = "Alice";
	private final String contentType = "text/plain";
	private final long timestamp = System.currentTimeMillis();
	private final String messageBody = "Hello world";
	private final Collection<MessageId> messageIds;
	private final TransportId transportId;
	private final TransportProperties transportProperties;

	public ProtocolIntegrationTest() throws Exception {
		Injector i = Guice.createInjector(new TestDatabaseModule(),
				new TestLifecycleModule(), new TestSystemModule(),
				new TestUiModule(), new CryptoModule(), new DatabaseModule(),
				new EventModule(), new MessagingModule(),
				new ReliabilityModule(), new SerialModule(),
				new TransportModule());
		streamReaderFactory = i.getInstance(StreamReaderFactory.class);
		streamWriterFactory = i.getInstance(StreamWriterFactory.class);
		packetReaderFactory = i.getInstance(PacketReaderFactory.class);
		packetWriterFactory = i.getInstance(PacketWriterFactory.class);
		messageVerifier = i.getInstance(MessageVerifier.class);
		contactId = new ContactId(234);
		// Create a shared secret
		secret = new byte[32];
		new Random().nextBytes(secret);
		// Create a group
		GroupFactory groupFactory = i.getInstance(GroupFactory.class);
		group = groupFactory.createGroup("Group");
		// Create an author
		AuthorFactory authorFactory = i.getInstance(AuthorFactory.class);
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		KeyPair authorKeyPair = crypto.generateSignatureKeyPair();
		author = authorFactory.createAuthor(authorName,
				authorKeyPair.getPublic().getEncoded());
		// Create two messages to the group: one anonymous, one pseudonymous
		MessageFactory messageFactory = i.getInstance(MessageFactory.class);
		message = messageFactory.createAnonymousMessage(null, group,
				contentType, timestamp, messageBody.getBytes("UTF-8"));
		message1 = messageFactory.createPseudonymousMessage(null, group,
				author, authorKeyPair.getPrivate(), contentType, timestamp,
				messageBody.getBytes("UTF-8"));
		messageIds = Arrays.asList(message.getId(), message1.getId());
		// Create some transport properties
		transportId = new TransportId("id");
		transportProperties = new TransportProperties(Collections.singletonMap(
				"bar", "baz"));
	}

	@Test
	public void testWriteAndRead() throws Exception {
		read(write());
	}

	private byte[] write() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamContext ctx = new StreamContext(contactId, transportId,
				secret.clone(), 0, true);
		StreamWriter streamWriter = streamWriterFactory.createStreamWriter(out,
				MAX_FRAME_LENGTH, ctx);
		OutputStream out1 = streamWriter.getOutputStream();
		PacketWriter packetWriter =
				packetWriterFactory.createPacketWriter(out1);

		packetWriter.writeAck(new Ack(messageIds));

		packetWriter.writeMessage(message.getSerialised());
		packetWriter.writeMessage(message1.getSerialised());

		packetWriter.writeOffer(new Offer(messageIds));

		packetWriter.writeRequest(new Request(messageIds));

		SubscriptionUpdate su = new SubscriptionUpdate(Arrays.asList(group), 1);
		packetWriter.writeSubscriptionUpdate(su);

		TransportUpdate tu = new TransportUpdate(transportId,
				transportProperties, 1);
		packetWriter.writeTransportUpdate(tu);

		out1.flush();
		return out.toByteArray();
	}

	private void read(byte[] connectionData) throws Exception {
		InputStream in = new ByteArrayInputStream(connectionData);
		byte[] tag = new byte[TAG_LENGTH];
		assertEquals(TAG_LENGTH, in.read(tag, 0, TAG_LENGTH));
		// FIXME: Check that the expected tag was received
		StreamContext ctx = new StreamContext(contactId, transportId,
				secret.clone(), 0, false);
		StreamReader streamReader = streamReaderFactory.createStreamReader(in,
				MAX_FRAME_LENGTH, ctx);
		InputStream in1 = streamReader.getInputStream();
		PacketReader packetReader = packetReaderFactory.createPacketReader(in1);

		// Read the ack
		assertTrue(packetReader.hasAck());
		Ack a = packetReader.readAck();
		assertEquals(messageIds, a.getMessageIds());

		// Read and verify the messages
		assertTrue(packetReader.hasMessage());
		UnverifiedMessage m = packetReader.readMessage();
		checkMessageEquality(message, messageVerifier.verifyMessage(m));
		assertTrue(packetReader.hasMessage());
		m = packetReader.readMessage();
		checkMessageEquality(message1, messageVerifier.verifyMessage(m));
		assertFalse(packetReader.hasMessage());

		// Read the offer
		assertTrue(packetReader.hasOffer());
		Offer o = packetReader.readOffer();
		assertEquals(messageIds, o.getMessageIds());

		// Read the request
		assertTrue(packetReader.hasRequest());
		Request req = packetReader.readRequest();
		assertEquals(messageIds, req.getMessageIds());

		// Read the subscription update
		assertTrue(packetReader.hasSubscriptionUpdate());
		SubscriptionUpdate su = packetReader.readSubscriptionUpdate();
		assertEquals(Arrays.asList(group), su.getGroups());
		assertEquals(1, su.getVersion());

		// Read the transport update
		assertTrue(packetReader.hasTransportUpdate());
		TransportUpdate tu = packetReader.readTransportUpdate();
		assertEquals(transportId, tu.getId());
		assertEquals(transportProperties, tu.getProperties());
		assertEquals(1, tu.getVersion());

		in.close();
	}

	private void checkMessageEquality(Message m1, Message m2) {
		assertEquals(m1.getId(), m2.getId());
		assertEquals(m1.getParent(), m2.getParent());
		assertEquals(m1.getGroup(), m2.getGroup());
		assertEquals(m1.getAuthor(), m2.getAuthor());
		assertEquals(m1.getTimestamp(), m2.getTimestamp());
		assertArrayEquals(m1.getSerialised(), m2.getSerialised());
	}
}
