package net.sf.briar.messaging;

import static net.sf.briar.api.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static net.sf.briar.api.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static net.sf.briar.api.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static net.sf.briar.api.TransportPropertyConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static net.sf.briar.api.TransportPropertyConstants.MAX_PROPERTY_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_BODY_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_CONTENT_TYPE_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_GROUP_NAME_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.messaging.MessagingConstants.MAX_SUBSCRIPTIONS;

import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestDatabaseModule;
import net.sf.briar.TestLifecycleModule;
import net.sf.briar.TestUtils;
import net.sf.briar.api.Author;
import net.sf.briar.api.AuthorFactory;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.UniqueId;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.messaging.Ack;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupFactory;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageFactory;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.messaging.Offer;
import net.sf.briar.api.messaging.PacketWriter;
import net.sf.briar.api.messaging.PacketWriterFactory;
import net.sf.briar.api.messaging.SubscriptionUpdate;
import net.sf.briar.api.messaging.TransportUpdate;
import net.sf.briar.clock.ClockModule;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.db.DatabaseModule;
import net.sf.briar.messaging.duplex.DuplexMessagingModule;
import net.sf.briar.messaging.simplex.SimplexMessagingModule;
import net.sf.briar.serial.SerialModule;
import net.sf.briar.transport.TransportModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConstantsTest extends BriarTestCase {

	private final CryptoComponent crypto;
	private final GroupFactory groupFactory;
	private final AuthorFactory authorFactory;
	private final MessageFactory messageFactory;
	private final PacketWriterFactory packetWriterFactory;

	public ConstantsTest() throws Exception {
		Injector i = Guice.createInjector(new TestDatabaseModule(),
				new TestLifecycleModule(), new ClockModule(),
				new CryptoModule(), new DatabaseModule(), new MessagingModule(),
				new DuplexMessagingModule(), new SimplexMessagingModule(),
				new SerialModule(), new TransportModule());
		crypto = i.getInstance(CryptoComponent.class);
		groupFactory = i.getInstance(GroupFactory.class);
		authorFactory = i.getInstance(AuthorFactory.class);
		messageFactory = i.getInstance(MessageFactory.class);
		packetWriterFactory = i.getInstance(PacketWriterFactory.class);
	}

	@Test
	public void testAgreementPublicKeys() throws Exception {
		// Generate 10 agreement key pairs
		for(int i = 0; i < 10; i++) {
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
		for(int i = 0; i < 10; i++) {
			KeyPair keyPair = crypto.generateSignatureKeyPair();
			// Check the length of the public key
			byte[] publicKey = keyPair.getPublic().getEncoded();
			assertTrue(publicKey.length <= MAX_PUBLIC_KEY_LENGTH);
			// Sign some random data and check the length of the signature
			byte[] toBeSigned = new byte[1000];
			random.nextBytes(toBeSigned);
			sig.initSign(keyPair.getPrivate());
			sig.update(toBeSigned);
			byte[] signature = sig.sign();
			assertTrue(signature.length <= MAX_SIGNATURE_LENGTH);
		}
	}

	@Test
	public void testMessageIdsFitIntoLargeAck() throws Exception {
		testMessageIdsFitIntoAck(MAX_PACKET_LENGTH);
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
		byte[] groupPublic = new byte[MAX_PUBLIC_KEY_LENGTH];
		Group group = groupFactory.createGroup(groupName, groupPublic);
		// Create a maximum-length author
		String authorName =
				TestUtils.createRandomString(MAX_AUTHOR_NAME_LENGTH);
		byte[] authorPublic = new byte[MAX_PUBLIC_KEY_LENGTH];
		Author author = authorFactory.createAuthor(authorName, authorPublic);
		// Create a maximum-length message
		PrivateKey groupPrivate =
				crypto.generateSignatureKeyPair().getPrivate();
		PrivateKey authorPrivate =
				crypto.generateSignatureKeyPair().getPrivate();
		String contentType =
				TestUtils.createRandomString(MAX_CONTENT_TYPE_LENGTH);
		byte[] body = new byte[MAX_BODY_LENGTH];
		Message message = messageFactory.createPseudonymousMessage(parent,
				group, groupPrivate, author, authorPrivate, contentType, body);
		// Check the size of the serialised message
		int length = message.getSerialised().length;
		assertTrue(length > UniqueId.LENGTH + MAX_GROUP_NAME_LENGTH
				+ MAX_PUBLIC_KEY_LENGTH + MAX_AUTHOR_NAME_LENGTH
				+ MAX_PUBLIC_KEY_LENGTH + MAX_CONTENT_TYPE_LENGTH
				+ MAX_BODY_LENGTH);
		assertTrue(length <= MAX_PACKET_LENGTH);
	}

	@Test
	public void testMessageIdsFitIntoLargeOffer() throws Exception {
		testMessageIdsFitIntoOffer(MAX_PACKET_LENGTH);
	}

	@Test
	public void testMessageIdsFitIntoSmallOffer() throws Exception {
		testMessageIdsFitIntoOffer(1000);
	}

	@Test
	public void testPropertiesFitIntoTransportUpdate() throws Exception {
		// Create the maximum number of properties with the maximum length
		TransportProperties p = new TransportProperties();
		for(int i = 0; i < MAX_PROPERTIES_PER_TRANSPORT; i++) {
			String key = TestUtils.createRandomString(MAX_PROPERTY_LENGTH);
			String value = TestUtils.createRandomString(MAX_PROPERTY_LENGTH);
			p.put(key, value);
		}
		// Create a maximum-length transport update
		TransportId id = new TransportId(TestUtils.getRandomId());
		TransportUpdate u = new TransportUpdate(id, p, Long.MAX_VALUE);
		// Serialise the update
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PacketWriter writer = packetWriterFactory.createPacketWriter(out, true);
		writer.writeTransportUpdate(u);
		// Check the size of the serialised transport update
		assertTrue(out.size() <= MAX_PACKET_LENGTH);
	}

	@Test
	public void testGroupsFitIntoSubscriptionUpdate() throws Exception {
		// Create the maximum number of maximum-length groups
		Collection<Group> subs = new ArrayList<Group>();
		for(int i = 0; i < MAX_SUBSCRIPTIONS; i++) {
			String groupName =
					TestUtils.createRandomString(MAX_GROUP_NAME_LENGTH);
			byte[] groupPublic = new byte[MAX_PUBLIC_KEY_LENGTH];
			subs.add(groupFactory.createGroup(groupName, groupPublic));
		}
		// Create a maximum-length subscription update
		SubscriptionUpdate u = new SubscriptionUpdate(subs, Long.MAX_VALUE);
		// Serialise the update
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PacketWriter writer = packetWriterFactory.createPacketWriter(out, true);
		writer.writeSubscriptionUpdate(u);
		// Check the size of the serialised subscription update
		assertTrue(out.size() <= MAX_PACKET_LENGTH);
	}

	private void testMessageIdsFitIntoAck(int length) throws Exception {
		// Create an ack with as many message IDs as possible
		ByteArrayOutputStream out = new ByteArrayOutputStream(length);
		PacketWriter writer = packetWriterFactory.createPacketWriter(out, true);
		int maxMessages = writer.getMaxMessagesForAck(length);
		Collection<MessageId> acked = new ArrayList<MessageId>();
		for(int i = 0; i < maxMessages; i++)
			acked.add(new MessageId(TestUtils.getRandomId()));
		writer.writeAck(new Ack(acked));
		// Check the size of the serialised ack
		assertTrue(out.size() <= length);
	}

	private void testMessageIdsFitIntoOffer(int length) throws Exception {
		// Create an offer with as many message IDs as possible
		ByteArrayOutputStream out = new ByteArrayOutputStream(length);
		PacketWriter writer = packetWriterFactory.createPacketWriter(out, true);
		int maxMessages = writer.getMaxMessagesForOffer(length);
		Collection<MessageId> offered = new ArrayList<MessageId>();
		for(int i = 0; i < maxMessages; i++)
			offered.add(new MessageId(TestUtils.getRandomId()));
		writer.writeOffer(new Offer(offered));
		// Check the size of the serialised offer
		assertTrue(out.size() <= length);
	}
}
