package net.sf.briar.protocol.writers;

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
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorFactory;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageFactory;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Transport;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.OfferWriter;
import net.sf.briar.api.protocol.writers.SubscriptionUpdateWriter;
import net.sf.briar.api.protocol.writers.TransportUpdateWriter;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.protocol.ProtocolModule;
import net.sf.briar.serial.SerialModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConstantsTest extends TestCase {

	private final WriterFactory writerFactory;
	private final CryptoComponent crypto;
	private final SerialComponent serial;
	private final GroupFactory groupFactory;
	private final AuthorFactory authorFactory;
	private final MessageFactory messageFactory;

	public ConstantsTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule(),
				new ProtocolModule(), new SerialModule());
		writerFactory = i.getInstance(WriterFactory.class);
		crypto = i.getInstance(CryptoComponent.class);
		serial = i.getInstance(SerialComponent.class);
		groupFactory = i.getInstance(GroupFactory.class);
		authorFactory = i.getInstance(AuthorFactory.class);
		messageFactory = i.getInstance(MessageFactory.class);
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
		AckWriter a = new AckWriterImpl(out, serial, writerFactory);
		a.setMaxPacketLength(length);
		while(a.writeBatchId(new BatchId(TestUtils.getRandomId())));
		a.finish();
		// Check the size of the serialised ack
		assertTrue(out.size() <= length);
	}

	@Test
	public void testEmptyAck() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		AckWriter a = new AckWriterImpl(out, serial, writerFactory);
		// There's not enough room for a batch ID
		a.setMaxPacketLength(4);
		assertFalse(a.writeBatchId(new BatchId(TestUtils.getRandomId())));
		// Check that nothing was written
		assertEquals(0, out.size());
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
		BatchWriter b = new BatchWriterImpl(out, serial, writerFactory,
				crypto.getMessageDigest());
		assertTrue(b.writeMessage(message.getSerialised()));
		b.finish();
		// Check the size of the serialised batch
		assertTrue(out.size() > UniqueId.LENGTH + MAX_GROUP_NAME_LENGTH
				+ MAX_PUBLIC_KEY_LENGTH + MAX_AUTHOR_NAME_LENGTH
				+ MAX_PUBLIC_KEY_LENGTH + MAX_BODY_LENGTH);
		assertTrue(out.size() <= MAX_PACKET_LENGTH);
	}

	@Test
	public void testEmptyBatch() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BatchWriter b = new BatchWriterImpl(out, serial, writerFactory,
				crypto.getMessageDigest());
		// There's not enough room for a message
		b.setMaxPacketLength(4);
		assertFalse(b.writeMessage(new byte[4]));
		// Check that nothing was written
		assertEquals(0, out.size());
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
		OfferWriter o = new OfferWriterImpl(out, serial, writerFactory);
		o.setMaxPacketLength(length);
		while(o.writeMessageId(new MessageId(TestUtils.getRandomId())));
		o.finish();
		// Check the size of the serialised offer
		assertTrue(out.size() <= length);
	}

	@Test
	public void testEmptyOffer() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		OfferWriter o = new OfferWriterImpl(out, serial, writerFactory);
		// There's not enough room for a message ID
		o.setMaxPacketLength(4);
		assertFalse(o.writeMessageId(new MessageId(TestUtils.getRandomId())));
		// Check that nothing was written
		assertEquals(0, out.size());
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
		SubscriptionUpdateWriter s =
			new SubscriptionUpdateWriterImpl(out, writerFactory);
		s.writeSubscriptions(subs, Long.MAX_VALUE);
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
		TransportUpdateWriter t =
			new TransportUpdateWriterImpl(out, writerFactory);
		t.writeTransports(transports, Long.MAX_VALUE);
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
