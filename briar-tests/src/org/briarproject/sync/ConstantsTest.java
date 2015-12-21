package org.briarproject.sync;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.briarproject.BriarTestCase;
import org.briarproject.TestDatabaseModule;
import org.briarproject.TestLifecycleModule;
import org.briarproject.TestSystemModule;
import org.briarproject.TestUtils;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.UniqueId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.forum.ForumConstants;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.messaging.MessagingConstants;
import org.briarproject.api.messaging.PrivateMessage;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.sync.Ack;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.GroupId;
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
import org.briarproject.forum.ForumModule;
import org.briarproject.messaging.MessagingModule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

import static org.briarproject.api.TransportPropertyConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static org.briarproject.api.TransportPropertyConstants.MAX_PROPERTY_LENGTH;
import static org.briarproject.api.TransportPropertyConstants.MAX_TRANSPORT_ID_LENGTH;
import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_POST_BODY_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MAX_GROUP_DESCRIPTOR_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MAX_PACKET_PAYLOAD_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MAX_SUBSCRIPTIONS;
import static org.junit.Assert.assertTrue;

public class ConstantsTest extends BriarTestCase {

	private final CryptoComponent crypto;
	private final GroupFactory groupFactory;
	private final AuthorFactory authorFactory;
	private final PrivateMessageFactory privateMessageFactory;
	private final ForumPostFactory forumPostFactory;
	private final PacketWriterFactory packetWriterFactory;

	public ConstantsTest() throws Exception {
		Injector i = Guice.createInjector(new TestDatabaseModule(),
				new TestLifecycleModule(), new TestSystemModule(),
				new CryptoModule(), new DatabaseModule(), new DataModule(),
				new EventModule(), new ForumModule(), new MessagingModule(),
				new SyncModule());
		crypto = i.getInstance(CryptoComponent.class);
		groupFactory = i.getInstance(GroupFactory.class);
		authorFactory = i.getInstance(AuthorFactory.class);
		privateMessageFactory = i.getInstance(PrivateMessageFactory.class);
		forumPostFactory = i.getInstance(ForumPostFactory.class);
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
			assertTrue(signature.length <= MAX_SIGNATURE_LENGTH);
		}
	}

	@Test
	public void testMessageIdsFitIntoLargeAck() throws Exception {
		testMessageIdsFitIntoAck(MAX_PACKET_PAYLOAD_LENGTH);
	}

	@Test
	public void testMessageIdsFitIntoSmallAck() throws Exception {
		testMessageIdsFitIntoAck(1000);
	}

	@Test
	public void testPrivateMessageFitsIntoPacket() throws Exception {
		// Create a maximum-length private message
		GroupId groupId = new GroupId(TestUtils.getRandomId());
		long timestamp = Long.MAX_VALUE;
		MessageId parent = new MessageId(TestUtils.getRandomId());
		String contentType = TestUtils.createRandomString(
				MessagingConstants.MAX_CONTENT_TYPE_LENGTH);
		byte[] body = new byte[MAX_PRIVATE_MESSAGE_BODY_LENGTH];
		PrivateMessage message = privateMessageFactory.createPrivateMessage(
				groupId, timestamp, parent, contentType, body);
		// Check the size of the serialised message
		int length = message.getMessage().getRaw().length;
		assertTrue(length > UniqueId.LENGTH + 8 + UniqueId.LENGTH
				+ MessagingConstants.MAX_CONTENT_TYPE_LENGTH
				+ MAX_PRIVATE_MESSAGE_BODY_LENGTH);
		assertTrue(length <= MAX_PACKET_PAYLOAD_LENGTH);
	}

	@Test
	public void testForumPostFitsIntoPacket() throws Exception {
		// Create a maximum-length author
		String authorName = TestUtils.createRandomString(
				MAX_AUTHOR_NAME_LENGTH);
		byte[] authorPublic = new byte[MAX_PUBLIC_KEY_LENGTH];
		Author author = authorFactory.createAuthor(authorName, authorPublic);
		// Create a maximum-length forum post
		GroupId groupId = new GroupId(TestUtils.getRandomId());
		long timestamp = Long.MAX_VALUE;
		MessageId parent = new MessageId(TestUtils.getRandomId());
		String contentType = TestUtils.createRandomString(
				ForumConstants.MAX_CONTENT_TYPE_LENGTH);
		byte[] body = new byte[MAX_FORUM_POST_BODY_LENGTH];
		PrivateKey privateKey = crypto.generateSignatureKeyPair().getPrivate();
		ForumPost post = forumPostFactory.createPseudonymousPost(groupId,
				timestamp, parent, author, contentType, body, privateKey);
		// Check the size of the serialised message
		int length = post.getMessage().getRaw().length;
		assertTrue(length > UniqueId.LENGTH + 8 + UniqueId.LENGTH
				+ MAX_AUTHOR_NAME_LENGTH + MAX_PUBLIC_KEY_LENGTH
				+ ForumConstants.MAX_CONTENT_TYPE_LENGTH
				+ MAX_FORUM_POST_BODY_LENGTH);
		assertTrue(length <= MAX_PACKET_PAYLOAD_LENGTH);
	}

	@Test
	public void testMessageIdsFitIntoLargeOffer() throws Exception {
		testMessageIdsFitIntoOffer(MAX_PACKET_PAYLOAD_LENGTH);
	}

	@Test
	public void testMessageIdsFitIntoSmallOffer() throws Exception {
		testMessageIdsFitIntoOffer(1000);
	}

	@Test
	public void testMessageIdsFitIntoLargeRequest() throws Exception {
		testMessageIdsFitIntoRequest(MAX_PACKET_PAYLOAD_LENGTH);
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
		assertTrue(out.size() <= MAX_PACKET_PAYLOAD_LENGTH);
	}

	@Test
	public void testGroupsFitIntoSubscriptionUpdate() throws Exception {
		// Create the maximum number of maximum-length groups
		Random random = new Random();
		ClientId clientId = new ClientId(TestUtils.getRandomId());
		Collection<Group> groups = new ArrayList<Group>();
		for (int i = 0; i < MAX_SUBSCRIPTIONS; i++) {
			byte[] descriptor = new byte[MAX_GROUP_DESCRIPTOR_LENGTH];
			random.nextBytes(descriptor);
			groups.add(groupFactory.createGroup(clientId, descriptor));
		}
		// Create a maximum-length subscription update
		SubscriptionUpdate u = new SubscriptionUpdate(groups, Long.MAX_VALUE);
		// Serialise the update
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PacketWriter writer = packetWriterFactory.createPacketWriter(out);
		writer.writeSubscriptionUpdate(u);
		// Check the size of the serialised subscription update
		assertTrue(out.size() <= MAX_PACKET_PAYLOAD_LENGTH);
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
