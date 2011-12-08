package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_AUTHOR_NAME_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_BODY_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_GROUPS;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_GROUP_NAME_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PROPERTY_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PUBLIC_KEY_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_SUBJECT_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_TRANSPORTS;

import java.io.ByteArrayOutputStream;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorFactory;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageFactory;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.PacketFactory;
import net.sf.briar.api.protocol.ProtocolWriter;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.RawBatch;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.serial.SerialModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConstantsTest extends TestCase {

	private final CryptoComponent crypto;
	private final GroupFactory groupFactory;
	private final AuthorFactory authorFactory;
	private final MessageFactory messageFactory;
	private final PacketFactory packetFactory;
	private final ProtocolWriterFactory protocolWriterFactory;

	public ConstantsTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule(),
				new ProtocolModule(), new SerialModule());
		crypto = i.getInstance(CryptoComponent.class);
		groupFactory = i.getInstance(GroupFactory.class);
		authorFactory = i.getInstance(AuthorFactory.class);
		messageFactory = i.getInstance(MessageFactory.class);
		packetFactory = i.getInstance(PacketFactory.class);
		protocolWriterFactory = i.getInstance(ProtocolWriterFactory.class);
	}

	@Test
	public void testBatchesFitIntoLargeAck() throws Exception {
		testBatchesFitIntoAck(MAX_PACKET_LENGTH);
	}

	@Test
	public void testBatchesFitIntoSmallAck() throws Exception {
		testBatchesFitIntoAck(1000);
	}

	private void testBatchesFitIntoAck(int length) throws Exception {
		// Create an ack with as many batch IDs as possible
		ByteArrayOutputStream out = new ByteArrayOutputStream(length);
		ProtocolWriter writer = protocolWriterFactory.createProtocolWriter(out,
				true);
		int maxBatches = writer.getMaxBatchesForAck(length);
		Collection<BatchId> acked = new ArrayList<BatchId>();
		for(int i = 0; i < maxBatches; i++) {
			acked.add(new BatchId(TestUtils.getRandomId()));
		}
		Ack a = packetFactory.createAck(acked);
		writer.writeAck(a);
		// Check the size of the serialised ack
		assertTrue(out.size() <= length);
	}

	@Test
	public void testMessageFitsIntoBatch() throws Exception {
		// Create a maximum-length group
		String groupName = createRandomString(MAX_GROUP_NAME_LENGTH);
		byte[] groupPublic = new byte[MAX_PUBLIC_KEY_LENGTH];
		Group group = groupFactory.createGroup(groupName, groupPublic);
		// Create a maximum-length author
		String authorName = createRandomString(MAX_AUTHOR_NAME_LENGTH);
		byte[] authorPublic = new byte[MAX_PUBLIC_KEY_LENGTH];
		Author author = authorFactory.createAuthor(authorName, authorPublic);
		// Create a maximum-length message
		PrivateKey groupPrivate = crypto.generateKeyPair().getPrivate();
		PrivateKey authorPrivate = crypto.generateKeyPair().getPrivate();
		String subject = createRandomString(MAX_SUBJECT_LENGTH);
		byte[] body = new byte[MAX_BODY_LENGTH];
		Message message = messageFactory.createMessage(null, group,
				groupPrivate, author, authorPrivate, subject, body);
		// Add the message to a batch
		ByteArrayOutputStream out =
			new ByteArrayOutputStream(MAX_PACKET_LENGTH);
		ProtocolWriter writer = protocolWriterFactory.createProtocolWriter(out,
				true);
		RawBatch b = packetFactory.createBatch(Collections.singletonList(
				message.getSerialised()));
		writer.writeBatch(b);
		// Check the size of the serialised batch
		assertTrue(out.size() > UniqueId.LENGTH + MAX_GROUP_NAME_LENGTH
				+ MAX_PUBLIC_KEY_LENGTH + MAX_AUTHOR_NAME_LENGTH
				+ MAX_PUBLIC_KEY_LENGTH + MAX_BODY_LENGTH);
		assertTrue(out.size() <= MAX_PACKET_LENGTH);
	}

	@Test
	public void testMessagesFitIntoLargeOffer() throws Exception {
		testMessagesFitIntoOffer(MAX_PACKET_LENGTH);
	}

	@Test
	public void testMessagesFitIntoSmallOffer() throws Exception {
		testMessagesFitIntoOffer(1000);
	}

	private void testMessagesFitIntoOffer(int length) throws Exception {
		// Create an offer with as many message IDs as possible
		ByteArrayOutputStream out = new ByteArrayOutputStream(length);
		ProtocolWriter writer = protocolWriterFactory.createProtocolWriter(out,
				true);
		int maxMessages = writer.getMaxMessagesForOffer(length);
		Collection<MessageId> offered = new ArrayList<MessageId>();
		for(int i = 0; i < maxMessages; i++) {
			offered.add(new MessageId(TestUtils.getRandomId()));
		}
		Offer o = packetFactory.createOffer(offered);
		writer.writeOffer(o);
		// Check the size of the serialised offer
		assertTrue(out.size() <= length);
	}

	@Test
	public void testSubscriptionsFitIntoUpdate() throws Exception {
		// Create the maximum number of maximum-length subscriptions
		Map<Group, Long> subs = new HashMap<Group, Long>(MAX_GROUPS);
		byte[] publicKey = new byte[MAX_PUBLIC_KEY_LENGTH];
		for(int i = 0; i < MAX_GROUPS; i++) {
			String name = createRandomString(MAX_GROUP_NAME_LENGTH);
			Group group = groupFactory.createGroup(name, publicKey);
			subs.put(group, Long.MAX_VALUE);
		}
		// Add the subscriptions to an update
		ByteArrayOutputStream out =
			new ByteArrayOutputStream(MAX_PACKET_LENGTH);
		ProtocolWriter writer = protocolWriterFactory.createProtocolWriter(out,
				true);
		SubscriptionUpdate s = packetFactory.createSubscriptionUpdate(subs,
				Long.MAX_VALUE);
		writer.writeSubscriptionUpdate(s);
		// Check the size of the serialised update
		assertTrue(out.size() > MAX_GROUPS *
				(MAX_GROUP_NAME_LENGTH + MAX_PUBLIC_KEY_LENGTH + 8) + 8);
		assertTrue(out.size() <= MAX_PACKET_LENGTH);
	}

	@Test
	public void testTransportsFitIntoUpdate() throws Exception {
		// Create the maximum number of plugins, each with the maximum number
		// of maximum-length properties
		Collection<Transport> transports = new ArrayList<Transport>();
		for(int i = 0; i < MAX_TRANSPORTS; i++) {
			TransportId id = new TransportId(TestUtils.getRandomId());
			TransportIndex index = new TransportIndex(i);
			Transport t = new Transport(id, index);
			for(int j = 0; j < MAX_PROPERTIES_PER_TRANSPORT; j++) {
				String key = createRandomString(MAX_PROPERTY_LENGTH);
				String value = createRandomString(MAX_PROPERTY_LENGTH);
				t.put(key, value);
			}
			transports.add(t);
		}
		// Add the transports to an update
		ByteArrayOutputStream out =
			new ByteArrayOutputStream(MAX_PACKET_LENGTH);
		ProtocolWriter writer = protocolWriterFactory.createProtocolWriter(out,
				true);
		TransportUpdate t = packetFactory.createTransportUpdate(transports,
				Long.MAX_VALUE);
		writer.writeTransportUpdate(t);
		// Check the size of the serialised update
		assertTrue(out.size() > MAX_TRANSPORTS * (UniqueId.LENGTH + 4
				+ (MAX_PROPERTIES_PER_TRANSPORT * MAX_PROPERTY_LENGTH * 2))
				+ 8);
		assertTrue(out.size() <= MAX_PACKET_LENGTH);
	}

	private static String createRandomString(int length) throws Exception {
		StringBuilder s = new StringBuilder(length);
		for(int i = 0; i < length; i++) {
			int digit = (int) (Math.random() * 10);
			s.append((char) ('0' + digit));
		}
		String string = s.toString();
		assertEquals(length, string.getBytes("UTF-8").length);
		return string;
	}
}
