package org.briarproject;

import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.sync.Ack;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.Offer;
import org.briarproject.api.sync.PacketReader;
import org.briarproject.api.sync.PacketReaderFactory;
import org.briarproject.api.sync.PacketWriter;
import org.briarproject.api.sync.PacketWriterFactory;
import org.briarproject.api.sync.Request;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;

import javax.inject.Inject;

import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SyncIntegrationTest extends BriarTestCase {

	@Inject
	GroupFactory groupFactory;
	@Inject
	MessageFactory messageFactory;
	@Inject
	StreamReaderFactory streamReaderFactory;
	@Inject
	StreamWriterFactory streamWriterFactory;
	@Inject
	PacketReaderFactory packetReaderFactory;
	@Inject
	PacketWriterFactory packetWriterFactory;
	@Inject
	CryptoComponent crypto;

	private final ContactId contactId;
	private final TransportId transportId;
	private final SecretKey tagKey, headerKey;
	private final long streamNumber;
	private final Message message, message1;
	private final Collection<MessageId> messageIds;

	public SyncIntegrationTest() throws Exception {

		SyncIntegrationTestComponent component =
				DaggerSyncIntegrationTestComponent.builder().build();
		component.inject(this);

		contactId = new ContactId(234);
		transportId = new TransportId("id");
		// Create the transport keys
		tagKey = TestUtils.getSecretKey();
		headerKey = TestUtils.getSecretKey();
		streamNumber = 123;
		// Create a group
		ClientId clientId = new ClientId(TestUtils.getRandomId());
		byte[] descriptor = new byte[0];
		Group group = groupFactory.createGroup(clientId, descriptor);
		// Add two messages to the group
		long timestamp = System.currentTimeMillis();
		byte[] body = "Hello world".getBytes("UTF-8");
		message = messageFactory.createMessage(group.getId(), timestamp, body);
		message1 = messageFactory.createMessage(group.getId(), timestamp, body);
		messageIds = Arrays.asList(message.getId(), message1.getId());
	}

	@Test
	public void testWriteAndRead() throws Exception {
		read(write());
	}

	private byte[] write() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StreamContext ctx = new StreamContext(contactId, transportId, tagKey,
				headerKey, streamNumber);
		OutputStream streamWriter = streamWriterFactory.createStreamWriter(out,
				ctx);
		PacketWriter packetWriter = packetWriterFactory.createPacketWriter(
				streamWriter);

		packetWriter.writeAck(new Ack(messageIds));
		packetWriter.writeMessage(message.getRaw());
		packetWriter.writeMessage(message1.getRaw());
		packetWriter.writeOffer(new Offer(messageIds));
		packetWriter.writeRequest(new Request(messageIds));

		streamWriter.flush();
		return out.toByteArray();
	}

	private void read(byte[] connectionData) throws Exception {
		// Calculate the expected tag
		byte[] expectedTag = new byte[TAG_LENGTH];
		crypto.encodeTag(expectedTag, tagKey, streamNumber);

		// Read the tag
		InputStream in = new ByteArrayInputStream(connectionData);
		byte[] tag = new byte[TAG_LENGTH];
		assertEquals(TAG_LENGTH, in.read(tag, 0, TAG_LENGTH));
		assertArrayEquals(expectedTag, tag);

		// Create the readers
		StreamContext ctx = new StreamContext(contactId, transportId, tagKey,
				headerKey, streamNumber);
		InputStream streamReader = streamReaderFactory.createStreamReader(in,
				ctx);
		PacketReader packetReader = packetReaderFactory.createPacketReader(
				streamReader);

		// Read the ack
		assertTrue(packetReader.hasAck());
		Ack a = packetReader.readAck();
		assertEquals(messageIds, a.getMessageIds());

		// Read the messages
		assertTrue(packetReader.hasMessage());
		Message m = packetReader.readMessage();
		checkMessageEquality(message, m);
		assertTrue(packetReader.hasMessage());
		m = packetReader.readMessage();
		checkMessageEquality(message1, m);
		assertFalse(packetReader.hasMessage());

		// Read the offer
		assertTrue(packetReader.hasOffer());
		Offer o = packetReader.readOffer();
		assertEquals(messageIds, o.getMessageIds());

		// Read the request
		assertTrue(packetReader.hasRequest());
		Request req = packetReader.readRequest();
		assertEquals(messageIds, req.getMessageIds());

		in.close();
	}

	private void checkMessageEquality(Message m1, Message m2) {
		assertArrayEquals(m1.getId().getBytes(), m2.getId().getBytes());
		assertArrayEquals(m1.getGroupId().getBytes(),
				m2.getGroupId().getBytes());
		assertEquals(m1.getTimestamp(), m2.getTimestamp());
		assertEquals(m1.getLength(), m2.getLength());
		assertArrayEquals(m1.getRaw(), m2.getRaw());
	}
}
