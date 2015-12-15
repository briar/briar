package org.briarproject.sync;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.briarproject.BriarTestCase;
import org.briarproject.TestDatabaseModule;
import org.briarproject.TestLifecycleModule;
import org.briarproject.TestSystemModule;
import org.briarproject.TestUtils;
import org.briarproject.api.Author;
import org.briarproject.api.AuthorFactory;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.UniqueId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.sync.Ack;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.Offer;
import org.briarproject.api.sync.PacketWriter;
import org.briarproject.api.sync.PacketWriterFactory;
import org.briarproject.api.sync.Request;
import org.briarproject.api.sync.SubscriptionUpdate;
import org.briarproject.api.sync.TransportUpdate;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.data.DataModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.event.EventModule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

import static org.briarproject.api.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.api.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.api.TransportPropertyConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static org.briarproject.api.TransportPropertyConstants.MAX_PROPERTY_LENGTH;
import static org.briarproject.api.TransportPropertyConstants.MAX_TRANSPORT_ID_LENGTH;
import static org.briarproject.api.sync.MessagingConstants.MAX_BODY_LENGTH;
import static org.briarproject.api.sync.MessagingConstants.MAX_CONTENT_TYPE_LENGTH;
import static org.briarproject.api.sync.MessagingConstants.MAX_GROUP_NAME_LENGTH;
import static org.briarproject.api.sync.MessagingConstants.MAX_PAYLOAD_LENGTH;
import static org.briarproject.api.sync.MessagingConstants.MAX_SUBSCRIPTIONS;
import static org.junit.Assert.assertTrue;

public class ConstantsTest extends BriarTestCase {

	private final CryptoComponent crypto;
	private final GroupFactory groupFactory;
	private final AuthorFactory authorFactory;
	private final MessageFactory messageFactory;
	private final PacketWriterFactory packetWriterFactory;

	public ConstantsTest() throws Exception {
		Injector i = Guice.createInjector(new TestDatabaseModule(),
				new TestLifecycleModule(), new TestSystemModule(),
				new CryptoModule(), new DatabaseModule(), new EventModule(),
				new org.briarproject.sync.MessagingModule(), new DataModule());
		crypto = i.getInstance(CryptoComponent.class);
		groupFactory = i.getInstance(GroupFactory.class);
		authorFactory = i.getInstance(AuthorFactory.class);
		messageFactory = i.getInstance(MessageFactory.class);
		packetWriterFactory = i.getInstance(PacketWriterFactory.class);
	}

	@Test
	public void testAgreementPublicKeys() throws Exception {
		// Generate 10 agreement key pairs
		for (int i = 0; i < 10; i++) {
			KeyPair keyPair = crypto.generateSignatureKeyPair();
			// Check the length of the public key
			byte[] publicKey = keyPair.getPublic().getEncoded();
			assertTrue(publicKey.length <= MAX_PUBLIC_KEY_LENGTH);
		}
	}

	@Test
	public void testSignaturePublicKeys() throws Exception {
		Random random = new Random();
		Signature sig = crypto.getSignature();
		// Generate 10 signature key pairs
		for (int i = 0; i < 10; i++) {
			KeyPair keyPair = crypto.generateSignatureKeyPair();
			// Check the length of the public key
			byte[] publicKey = keyPair.getPublic().getEncoded();
			assertTrue(publicKey.length <= MAX_PUBLIC_KEY_LENGTH);
			// Sign some random data and check the length of the signature
			byte[] toBeSigned = new byte[1234];
			random.nextBytes(toBeSigned);
			sig.initSign(keyPair.getPrivate());
			sig.update(toBeSigned);
			byte[] signature = sig.sign();
			assertTrue("Length " + signature.length,
					signature.length <= MAX_SIGNATURE_LENGTH);
		}
	}

	@Test
	public void testMessageIdsFitIntoLargeAck() throws Exception {
		testMessageIdsFitIntoAck(MAX_PAYLOAD_LENGTH);
	}

	@Test
	public void testMessageIdsFitIntoSmallAck() throws Exception {
		testMessageIdsFitIntoAck(1000);
	}

	@Test
	public void testMessageFitsIntoPacket() throws Exception {
		MessageId parent = new MessageId(TestUtils.getRandomId());
		// Create a maximum-length group
		String groupName = TestUtils.createRandomString(MAX_GROUP_NAME_LENGTH);
		Group group = groupFactory.createGroup(groupName);
		// Create a maximum-length author
		String authorName =
				TestUtils.createRandomString(MAX_AUTHOR_NAME_LENGTH);
		byte[] authorPublic = new byte[MAX_PUBLIC_KEY_LENGTH];
		Author author = authorFactory.createAuthor(authorName, authorPublic);
		// Create a maximum-length message
		PrivateKey privateKey = crypto.generateSignatureKeyPair().getPrivate();
		String contentType =
				TestUtils.createRandomString(MAX_CONTENT_TYPE_LENGTH);
		long timestamp = Long.MAX_VALUE;
		byte[] body = new byte[MAX_BODY_LENGTH];
		Message message = messageFactory.createPseudonymousMessage(parent,
				group, author, privateKey, contentType, timestamp, body);
		// Check the size of the serialised message
		int length = message.getSerialised().length;
		assertTrue(length > UniqueId.LENGTH + MAX_GROUP_NAME_LENGTH
				+ MAX_PUBLIC_KEY_LENGTH + MAX_AUTHOR_NAME_LENGTH
				+ MAX_PUBLIC_KEY_LENGTH + MAX_CONTENT_TYPE_LENGTH
				+ MAX_BODY_LENGTH);
		assertTrue(length <= MAX_PAYLOAD_LENGTH);
	}

	@Test
	public void testMessageIdsFitIntoLargeOffer() throws Exception {
		testMessageIdsFitIntoOffer(MAX_PAYLOAD_LENGTH);
	}

	@Test
	public void testMessageIdsFitIntoSmallOffer() throws Exception {
		testMessageIdsFitIntoOffer(1000);
	}

	@Test
	public void testMessageIdsFitIntoLargeRequest() throws Exception {
		testMessageIdsFitIntoRequest(MAX_PAYLOAD_LENGTH);
	}

	@Test
	public void testMessageIdsFitIntoSmallRequest() throws Exception {
		testMessageIdsFitIntoRequest(1000);
	}

	@Test
	public void testPropertiesFitIntoTransportUpdate() throws Exception {
		// Create the maximum number of properties with the maximum length
		TransportProperties p = new TransportProperties();
		for (int i = 0; i < MAX_PROPERTIES_PER_TRANSPORT; i++) {
			String key = TestUtils.createRandomString(MAX_PROPERTY_LENGTH);
			String value = TestUtils.createRandomString(MAX_PROPERTY_LENGTH);
			p.put(key, value);
		}
		// Create a maximum-length transport update
		String idString = TestUtils.createRandomString(MAX_TRANSPORT_ID_LENGTH);
		TransportId id = new TransportId(idString);
		TransportUpdate u = new TransportUpdate(id, p, Long.MAX_VALUE);
		// Serialise the update
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PacketWriter writer = packetWriterFactory.createPacketWriter(out);
		writer.writeTransportUpdate(u);
		// Check the size of the serialised transport update
		assertTrue(out.size() <= MAX_PAYLOAD_LENGTH);
	}

	@Test
	public void testGroupsFitIntoSubscriptionUpdate() throws Exception {
		// Create the maximum number of maximum-length groups
		Collection<Group> groups = new ArrayList<Group>();
		for (int i = 0; i < MAX_SUBSCRIPTIONS; i++) {
			String name = TestUtils.createRandomString(MAX_GROUP_NAME_LENGTH);
			groups.add(groupFactory.createGroup(name));
		}
		// Create a maximum-length subscription update
		SubscriptionUpdate u = new SubscriptionUpdate(groups, Long.MAX_VALUE);
		// Serialise the update
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PacketWriter writer = packetWriterFactory.createPacketWriter(out);
		writer.writeSubscriptionUpdate(u);
		// Check the size of the serialised subscription update
		assertTrue(out.size() <= MAX_PAYLOAD_LENGTH);
	}

	private void testMessageIdsFitIntoAck(int length) throws Exception {
		// Create an ack with as many message IDs as possible
		ByteArrayOutputStream out = new ByteArrayOutputStream(length);
		PacketWriter writer = packetWriterFactory.createPacketWriter(out);
		int maxMessages = writer.getMaxMessagesForAck(length);
		Collection<MessageId> ids = new ArrayList<MessageId>();
		for (int i = 0; i < maxMessages; i++)
			ids.add(new MessageId(TestUtils.getRandomId()));
		writer.writeAck(new Ack(ids));
		// Check the size of the serialised ack
		assertTrue(out.size() <= length);
	}

	private void testMessageIdsFitIntoRequest(int length) throws Exception {
		// Create a request with as many message IDs as possible
		ByteArrayOutputStream out = new ByteArrayOutputStream(length);
		PacketWriter writer = packetWriterFactory.createPacketWriter(out);
		int maxMessages = writer.getMaxMessagesForRequest(length);
		Collection<MessageId> ids = new ArrayList<MessageId>();
		for (int i = 0; i < maxMessages; i++)
			ids.add(new MessageId(TestUtils.getRandomId()));
		writer.writeRequest(new Request(ids));
		// Check the size of the serialised request
		assertTrue(out.size() <= length);
	}

	private void testMessageIdsFitIntoOffer(int length) throws Exception {
		// Create an offer with as many message IDs as possible
		ByteArrayOutputStream out = new ByteArrayOutputStream(length);
		PacketWriter writer = packetWriterFactory.createPacketWriter(out);
		int maxMessages = writer.getMaxMessagesForOffer(length);
		Collection<MessageId> ids = new ArrayList<MessageId>();
		for (int i = 0; i < maxMessages; i++)
			ids.add(new MessageId(TestUtils.getRandomId()));
		writer.writeOffer(new Offer(ids));
		// Check the size of the serialised offer
		assertTrue(out.size() <= length);
	}
}
