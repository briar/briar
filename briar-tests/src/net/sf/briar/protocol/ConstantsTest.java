package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_AUTHOR_NAME_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_BODY_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_GROUP_NAME_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PUBLIC_KEY_LENGTH;
import static net.sf.briar.api.protocol.ProtocolConstants.MAX_SUBJECT_LENGTH;

import java.io.ByteArrayOutputStream;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collection;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.Author;
import net.sf.briar.api.protocol.AuthorFactory;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.MessageFactory;
import net.sf.briar.api.protocol.MessageId;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.ProtocolWriter;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.clock.ClockModule;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.serial.SerialModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConstantsTest extends BriarTestCase {

	private final CryptoComponent crypto;
	private final GroupFactory groupFactory;
	private final AuthorFactory authorFactory;
	private final MessageFactory messageFactory;
	private final ProtocolWriterFactory protocolWriterFactory;

	public ConstantsTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new ClockModule(), new CryptoModule(),
				new ProtocolModule(), new SerialModule());
		crypto = i.getInstance(CryptoComponent.class);
		groupFactory = i.getInstance(GroupFactory.class);
		authorFactory = i.getInstance(AuthorFactory.class);
		messageFactory = i.getInstance(MessageFactory.class);
		protocolWriterFactory = i.getInstance(ProtocolWriterFactory.class);
	}

	@Test
	public void testMessageIdsFitIntoLargeAck() throws Exception {
		testMessageIdsFitIntoAck(MAX_PACKET_LENGTH);
	}

	@Test
	public void testMessageIdsFitIntoSmallAck() throws Exception {
		testMessageIdsFitIntoAck(1000);
	}

	private void testMessageIdsFitIntoAck(int length) throws Exception {
		// Create an ack with as many message IDs as possible
		ByteArrayOutputStream out = new ByteArrayOutputStream(length);
		ProtocolWriter writer = protocolWriterFactory.createProtocolWriter(out,
				true);
		int maxMessages = writer.getMaxMessagesForAck(length);
		Collection<MessageId> acked = new ArrayList<MessageId>();
		for(int i = 0; i < maxMessages; i++) {
			acked.add(new MessageId(TestUtils.getRandomId()));
		}
		writer.writeAck(new Ack(acked));
		// Check the size of the serialised ack
		assertTrue(out.size() <= length);
	}

	@Test
	public void testMessageFitsIntoPacket() throws Exception {
		// Create a maximum-length group
		String groupName = createRandomString(MAX_GROUP_NAME_LENGTH);
		byte[] groupPublic = new byte[MAX_PUBLIC_KEY_LENGTH];
		Group group = groupFactory.createGroup(groupName, groupPublic);
		// Create a maximum-length author
		String authorName = createRandomString(MAX_AUTHOR_NAME_LENGTH);
		byte[] authorPublic = new byte[MAX_PUBLIC_KEY_LENGTH];
		Author author = authorFactory.createAuthor(authorName, authorPublic);
		// Create a maximum-length message
		PrivateKey groupPrivate = crypto.generateSignatureKeyPair().getPrivate();
		PrivateKey authorPrivate = crypto.generateSignatureKeyPair().getPrivate();
		String subject = createRandomString(MAX_SUBJECT_LENGTH);
		byte[] body = new byte[MAX_BODY_LENGTH];
		Message message = messageFactory.createMessage(null, group,
				groupPrivate, author, authorPrivate, subject, body);
		// Check the size of the serialised message
		int length = message.getSerialised().length;
		assertTrue(length > UniqueId.LENGTH + MAX_GROUP_NAME_LENGTH
				+ MAX_PUBLIC_KEY_LENGTH + MAX_AUTHOR_NAME_LENGTH
				+ MAX_PUBLIC_KEY_LENGTH + MAX_BODY_LENGTH);
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

	private void testMessageIdsFitIntoOffer(int length) throws Exception {
		// Create an offer with as many message IDs as possible
		ByteArrayOutputStream out = new ByteArrayOutputStream(length);
		ProtocolWriter writer = protocolWriterFactory.createProtocolWriter(out,
				true);
		int maxMessages = writer.getMaxMessagesForOffer(length);
		Collection<MessageId> offered = new ArrayList<MessageId>();
		for(int i = 0; i < maxMessages; i++) {
			offered.add(new MessageId(TestUtils.getRandomId()));
		}
		writer.writeOffer(new Offer(offered));
		// Check the size of the serialised offer
		assertTrue(out.size() <= length);
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
