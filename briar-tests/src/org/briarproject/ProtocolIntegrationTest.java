package org.briarproject;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.sync.Ack;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.MessageVerifier;
import org.briarproject.api.sync.Offer;
import org.briarproject.api.sync.PacketReader;
import org.briarproject.api.sync.PacketReaderFactory;
import org.briarproject.api.sync.PacketWriter;
import org.briarproject.api.sync.PacketWriterFactory;
import org.briarproject.api.sync.Request;
import org.briarproject.api.sync.SubscriptionUpdate;
import org.briarproject.api.sync.TransportUpdate;
import org.briarproject.api.sync.UnverifiedMessage;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.data.DataModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.event.EventModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.transport.TransportModule;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProtocolIntegrationTest extends BriarTestCase {

	private final StreamReaderFactory streamReaderFactory;
	private final StreamWriterFactory streamWriterFactory;
	private final PacketReaderFactory packetReaderFactory;
	private final PacketWriterFactory packetWriterFactory;
	private final MessageVerifier messageVerifier;

	private final ContactId contactId;
	private final SecretKey tagKey, headerKey;
	private final Group group;
	private final Message message, message1;
	private final Collection<MessageId> messageIds;
	private final TransportId transportId;
	private final TransportProperties transportProperties;

	public ProtocolIntegrationTest() throws Exception {
		Injector i = Guice.createInjector(new TestDatabaseModule(),
				new TestLifecycleModule(), new TestSystemModule(),
				new CryptoModule(), new DatabaseModule(), new EventModule(),
				new SyncModule(), new DataModule(),
				new TransportModule());
		streamReaderFactory = i.getInstance(StreamReaderFactory.class);
		streamWriterFactory = i.getInstance(StreamWriterFactory.class);
		packetReaderFactory = i.getInstance(PacketReaderFactory.class);
		packetWriterFactory = i.getInstance(PacketWriterFactory.class);
		messageVerifier = i.getInstance(MessageVerifier.class);
		contactId = new ContactId(234);
		// Create the transport keys
		tagKey = TestUtils.createSecretKey();
		headerKey = TestUtils.createSecretKey();
		// Create a group
		GroupFactory groupFactory = i.getInstance(GroupFactory.class);
		group = groupFactory.createGroup("Group");
		// Create an author
		AuthorFactory authorFactory = i.getInstance(AuthorFactory.class);
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		KeyPair authorKeyPair = crypto.generateSignatureKeyPair();
		Author author = authorFactory.createAuthor("Alice",
				authorKeyPair.getPublic().getEncoded());
		// Create two messages to the group: one anonymous, one pseudonymous
		MessageFactory messageFactory = i.getInstance(MessageFactory.class);
		String contentType = "text/plain";
		long timestamp = System.currentTimeMillis();
		String messageBody = "Hello world";
		message = messageFactory.createAnonymousMessage(null, group,
				"text/plain", timestamp, messageBody.getBytes("UTF-8"));
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
		StreamContext ctx = new StreamContext(contactId, transportId, tagKey,
				headerKey, 0);
		OutputStream streamWriter =
				streamWriterFactory.createStreamWriter(out, ctx);
		PacketWriter packetWriter = packetWriterFactory.createPacketWriter(
				streamWriter);

		packetWriter.writeAck(new Ack(messageIds));

		packetWriter.writeMessage(message.getSerialised());
		packetWriter.writeMessage(message1.getSerialised());

		packetWriter.writeOffer(new Offer(messageIds));

		packetWriter.writeRequest(new Request(messageIds));

		SubscriptionUpdate su = new SubscriptionUpdate(
				Collections.singletonList(group), 1);
		packetWriter.writeSubscriptionUpdate(su);

		TransportUpdate tu = new TransportUpdate(transportId,
				transportProperties, 1);
		packetWriter.writeTransportUpdate(tu);

		streamWriter.flush();
		return out.toByteArray();
	}

	private void read(byte[] connectionData) throws Exception {
		InputStream in = new ByteArrayInputStream(connectionData);
		byte[] tag = new byte[TAG_LENGTH];
		assertEquals(TAG_LENGTH, in.read(tag, 0, TAG_LENGTH));
		// FIXME: Check that the expected tag was received
		StreamContext ctx = new StreamContext(contactId, transportId, tagKey,
				headerKey, 0);
		InputStream streamReader =
				streamReaderFactory.createStreamReader(in, ctx);
		PacketReader packetReader = packetReaderFactory.createPacketReader(
				streamReader);

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
		assertEquals(Collections.singletonList(group), su.getGroups());
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
