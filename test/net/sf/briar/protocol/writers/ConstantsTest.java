package net.sf.briar.protocol.writers;

import java.io.ByteArrayOutputStream;
import java.security.PrivateKey;
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
import net.sf.briar.api.protocol.MessageEncoder;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.SubscriptionUpdate;
import net.sf.briar.api.protocol.TransportUpdate;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.protocol.writers.AckWriter;
import net.sf.briar.api.protocol.writers.BatchWriter;
import net.sf.briar.api.protocol.writers.OfferWriter;
import net.sf.briar.api.protocol.writers.SubscriptionWriter;
import net.sf.briar.api.protocol.writers.TransportWriter;
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
	private final GroupFactory groupFactory;
	private final AuthorFactory authorFactory;
	private final MessageEncoder messageEncoder;

	public ConstantsTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule(),
				new ProtocolModule(), new SerialModule());
		writerFactory = i.getInstance(WriterFactory.class);
		crypto = i.getInstance(CryptoComponent.class);
		groupFactory = i.getInstance(GroupFactory.class);
		authorFactory = i.getInstance(AuthorFactory.class);
		messageEncoder = i.getInstance(MessageEncoder.class);
	}

	@Test
	public void testBatchesFitIntoAck() throws Exception {
		// Create an ack with the maximum number of batch IDs
		ByteArrayOutputStream out = new ByteArrayOutputStream(
				ProtocolConstants.MAX_PACKET_LENGTH);
		AckWriter a = new AckWriterImpl(out, writerFactory);
		for(int i = 0; i < Ack.MAX_IDS_PER_ACK; i++) {
			assertTrue(a.writeBatchId(new BatchId(TestUtils.getRandomId())));
		}
		// Check that no more batch IDs can be written
		assertFalse(a.writeBatchId(new BatchId(TestUtils.getRandomId())));
		a.finish();
		// Check the size of the serialised ack
		assertTrue(out.size() > UniqueId.LENGTH * Ack.MAX_IDS_PER_ACK);
		assertTrue(out.size() <= ProtocolConstants.MAX_PACKET_LENGTH);
	}

	@Test
	public void testMessageFitsIntoBatch() throws Exception {
		// Create a maximum-length group
		String groupName = createRandomString(Group.MAX_NAME_LENGTH);
		byte[] groupPublic = new byte[Group.MAX_PUBLIC_KEY_LENGTH];
		Group group = groupFactory.createGroup(groupName, groupPublic);
		// Create a maximum-length author
		String authorName = createRandomString(Author.MAX_NAME_LENGTH);
		byte[] authorPublic = new byte[Author.MAX_PUBLIC_KEY_LENGTH];
		Author author = authorFactory.createAuthor(authorName, authorPublic);
		// Create a maximum-length message
		PrivateKey groupPrivate = crypto.generateKeyPair().getPrivate();
		PrivateKey authorPrivate = crypto.generateKeyPair().getPrivate();
		byte[] body = new byte[Message.MAX_BODY_LENGTH];
		Message message = messageEncoder.encodeMessage(MessageId.NONE, group,
				groupPrivate, author, authorPrivate, body);
		// Add the message to a batch
		ByteArrayOutputStream out = new ByteArrayOutputStream(
				ProtocolConstants.MAX_PACKET_LENGTH);
		BatchWriter b = new BatchWriterImpl(out, writerFactory,
				crypto.getMessageDigest());
		b.writeMessage(message.getBytes());
		b.finish();
		// Check the size of the serialised batch
		assertTrue(out.size() > UniqueId.LENGTH + Group.MAX_NAME_LENGTH +
				Group.MAX_PUBLIC_KEY_LENGTH + Author.MAX_NAME_LENGTH +
				Author.MAX_PUBLIC_KEY_LENGTH + Message.MAX_BODY_LENGTH);
		assertTrue(out.size() <= ProtocolConstants.MAX_PACKET_LENGTH);
	}

	@Test
	public void testMessagesFitIntoOffer() throws Exception {
		// Create an offer with the maximum number of message IDs
		ByteArrayOutputStream out = new ByteArrayOutputStream(
				ProtocolConstants.MAX_PACKET_LENGTH);
		OfferWriter o = new OfferWriterImpl(out, writerFactory);
		for(int i = 0; i < Offer.MAX_IDS_PER_OFFER; i++) {
			assertTrue(o.writeMessageId(new MessageId(
					TestUtils.getRandomId())));
		}
		// Check that no more message IDs can be written
		assertFalse(o.writeMessageId(new MessageId(TestUtils.getRandomId())));
		o.finish();
		// Check the size of the serialised offer
		assertTrue(out.size() > UniqueId.LENGTH * Offer.MAX_IDS_PER_OFFER);
		assertTrue(out.size() <= ProtocolConstants.MAX_PACKET_LENGTH);
	}

	@Test
	public void testSubscriptionsFitIntoUpdate() throws Exception {
		// Create the maximum number of maximum-length subscriptions
		Map<Group, Long> subs =
			new HashMap<Group, Long>(SubscriptionUpdate.MAX_SUBS_PER_UPDATE);
		byte[] publicKey = new byte[Group.MAX_PUBLIC_KEY_LENGTH];
		for(int i = 0; i < SubscriptionUpdate.MAX_SUBS_PER_UPDATE; i++) {
			String name = createRandomString(Group.MAX_NAME_LENGTH);
			Group group = groupFactory.createGroup(name, publicKey);
			subs.put(group, Long.MAX_VALUE);
		}
		// Add the subscriptions to an update
		ByteArrayOutputStream out = new ByteArrayOutputStream(
				ProtocolConstants.MAX_PACKET_LENGTH);
		SubscriptionWriter s = new SubscriptionWriterImpl(out, writerFactory);
		s.writeSubscriptions(subs, Long.MAX_VALUE);
		// Check the size of the serialised update
		assertTrue(out.size() > SubscriptionUpdate.MAX_SUBS_PER_UPDATE *
				(Group.MAX_NAME_LENGTH + Group.MAX_PUBLIC_KEY_LENGTH + 8) + 8);
		assertTrue(out.size() <= ProtocolConstants.MAX_PACKET_LENGTH);
	}

	@Test
	public void testTransportsFitIntoUpdate() throws Exception {
		// Create the maximum number of plugins, each with the maximum number
		// of maximum-length properties
		Map<String, Map<String, String>> transports =
			new HashMap<String, Map<String, String>>(
					TransportUpdate.MAX_PLUGINS_PER_UPDATE);
		for(int i = 0; i < TransportUpdate.MAX_PLUGINS_PER_UPDATE; i++) {
			String name = createRandomString(TransportUpdate.MAX_NAME_LENGTH);
			Map<String, String> properties = new HashMap<String, String>(
					TransportUpdate.MAX_PROPERTIES_PER_PLUGIN);
			for(int j = 0; j < TransportUpdate.MAX_PROPERTIES_PER_PLUGIN; j++) {
				String key = createRandomString(
						TransportUpdate.MAX_KEY_OR_VALUE_LENGTH);
				String value = createRandomString(
						TransportUpdate.MAX_KEY_OR_VALUE_LENGTH);
				properties.put(key, value);
			}
			transports.put(name, properties);
		}
		// Add the transports to an update
		ByteArrayOutputStream out = new ByteArrayOutputStream(
				ProtocolConstants.MAX_PACKET_LENGTH);
		TransportWriter t = new TransportWriterImpl(out, writerFactory);
		t.writeTransports(transports, Long.MAX_VALUE);
		// Check the size of the serialised update
		assertTrue(out.size() > TransportUpdate.MAX_PLUGINS_PER_UPDATE *
				(TransportUpdate.MAX_NAME_LENGTH +
						TransportUpdate.MAX_PROPERTIES_PER_PLUGIN *
						TransportUpdate.MAX_KEY_OR_VALUE_LENGTH * 2) + 8);
		assertTrue(out.size() <= ProtocolConstants.MAX_PACKET_LENGTH);
	}

	private static String createRandomString(int length) {
		StringBuilder s = new StringBuilder(length);
		for(int i = 0; i < length; i++) {
			int digit = (int) (Math.random() * 10);
			s.append((char) ('0' + digit));
		}
		return s.toString();
	}
}
