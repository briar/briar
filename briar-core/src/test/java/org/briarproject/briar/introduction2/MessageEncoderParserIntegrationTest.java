package org.briarproject.briar.introduction2;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.briarproject.briar.test.DaggerBriarIntegrationTestComponent;
import org.junit.Test;

import java.util.Map;

import javax.inject.Inject;

import static org.briarproject.bramble.api.crypto.CryptoConstants.MAC_BYTES;
import static org.briarproject.bramble.api.crypto.CryptoConstants.MAX_SIGNATURE_BYTES;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getTransportPropertiesMap;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MAX_INTRODUCTION_MESSAGE_LENGTH;
import static org.briarproject.briar.introduction2.MessageType.ABORT;
import static org.briarproject.briar.introduction2.MessageType.REQUEST;
import static org.briarproject.briar.test.BriarTestUtils.getRealAuthor;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MessageEncoderParserIntegrationTest extends BrambleTestCase {

	@Inject
	ClientHelper clientHelper;
	@Inject
	MessageFactory messageFactory;
	@Inject
	AuthorFactory authorFactory;

	private final MessageEncoder messageEncoder;
	private final MessageParser messageParser;

	private final GroupId groupId = new GroupId(getRandomId());
	private final long timestamp = 42L;
	private final SessionId sessionId = new SessionId(getRandomId());
	private final MessageId previousMsgId = new MessageId(getRandomId());
	private final Author author;
	private final String text =
			getRandomString(MAX_INTRODUCTION_MESSAGE_LENGTH);
	private final byte[] ephemeralPublicKey =
			getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
	private final byte[] mac = getRandomBytes(MAC_BYTES);
	private final byte[] signature = getRandomBytes(MAX_SIGNATURE_BYTES);

	public MessageEncoderParserIntegrationTest() {
		BriarIntegrationTestComponent component =
				DaggerBriarIntegrationTestComponent.builder().build();
		component.inject(this);

		messageEncoder = new MessageEncoderImpl(clientHelper, messageFactory);
		messageParser = new MessageParserImpl(clientHelper);
		author = getRealAuthor(authorFactory);
	}

	@Test
	public void testRequestMessageMetadata() throws FormatException {
		BdfDictionary d = messageEncoder
				.encodeRequestMetadata(timestamp, true, false, false,
						true);
		MessageMetadata meta = messageParser.parseMetadata(d);

		assertEquals(REQUEST, meta.getMessageType());
		assertNull(meta.getSessionId());
		assertEquals(timestamp, meta.getTimestamp());
		assertTrue(meta.isLocal());
		assertFalse(meta.isRead());
		assertFalse(meta.isVisibleInConversation());
		assertFalse(meta.isAvailableToAnswer());
		assertTrue(meta.wasAccepted());
	}

	@Test
	public void testMessageMetadata() throws FormatException {
		BdfDictionary d = messageEncoder
				.encodeMetadata(ABORT, sessionId, timestamp, false, true,
						false);
		MessageMetadata meta = messageParser.parseMetadata(d);

		assertEquals(ABORT, meta.getMessageType());
		assertEquals(sessionId, meta.getSessionId());
		assertEquals(timestamp, meta.getTimestamp());
		assertFalse(meta.isLocal());
		assertTrue(meta.isRead());
		assertFalse(meta.isVisibleInConversation());
		assertFalse(meta.isAvailableToAnswer());
		assertFalse(meta.wasAccepted());
	}

	@Test
	public void testRequestMessage() throws FormatException {
		Message m = messageEncoder
				.encodeRequestMessage(groupId, timestamp, previousMsgId, author,
						text);
		RequestMessage rm =
				messageParser.parseRequestMessage(m, clientHelper.toList(m));

		assertEquals(m.getId(), rm.getMessageId());
		assertEquals(m.getGroupId(), rm.getGroupId());
		assertEquals(m.getTimestamp(), rm.getTimestamp());
		assertEquals(previousMsgId, rm.getPreviousMessageId());
		assertEquals(author, rm.getAuthor());
		assertEquals(text, rm.getMessage());
	}

	@Test
	public void testRequestMessageWithPreviousMsgNull() throws FormatException {
		Message m = messageEncoder
				.encodeRequestMessage(groupId, timestamp, null, author, text);
		RequestMessage rm =
				messageParser.parseRequestMessage(m, clientHelper.toList(m));

		assertNull(rm.getPreviousMessageId());
	}

	@Test
	public void testRequestMessageWithMsgNull() throws FormatException {
		Message m = messageEncoder
				.encodeRequestMessage(groupId, timestamp, previousMsgId, author,
						null);
		RequestMessage rm =
				messageParser.parseRequestMessage(m, clientHelper.toList(m));

		assertNull(rm.getMessage());
	}

	@Test
	public void testAcceptMessage() throws Exception {
		Map<TransportId, TransportProperties> transportProperties =
				getTransportPropertiesMap(2);

		long acceptTimestamp = 1337L;
		Message m = messageEncoder
				.encodeAcceptMessage(groupId, timestamp, previousMsgId,
						sessionId, ephemeralPublicKey, acceptTimestamp,
						transportProperties);
		AcceptMessage rm =
				messageParser.parseAcceptMessage(m, clientHelper.toList(m));

		assertEquals(m.getId(), rm.getMessageId());
		assertEquals(m.getGroupId(), rm.getGroupId());
		assertEquals(m.getTimestamp(), rm.getTimestamp());
		assertEquals(previousMsgId, rm.getPreviousMessageId());
		assertEquals(sessionId, rm.getSessionId());
		assertArrayEquals(ephemeralPublicKey, rm.getEphemeralPublicKey());
		assertEquals(acceptTimestamp, rm.getAcceptTimestamp());
		assertEquals(transportProperties, rm.getTransportProperties());
	}

	@Test
	public void testDeclineMessage() throws Exception {
		Message m = messageEncoder
				.encodeDeclineMessage(groupId, timestamp, previousMsgId,
						sessionId);
		DeclineMessage rm =
				messageParser.parseDeclineMessage(m, clientHelper.toList(m));

		assertEquals(m.getId(), rm.getMessageId());
		assertEquals(m.getGroupId(), rm.getGroupId());
		assertEquals(m.getTimestamp(), rm.getTimestamp());
		assertEquals(previousMsgId, rm.getPreviousMessageId());
		assertEquals(sessionId, rm.getSessionId());
	}

	@Test
	public void testAuthMessage() throws Exception {
		Message m = messageEncoder
				.encodeAuthMessage(groupId, timestamp, previousMsgId,
						sessionId, mac, signature);
		AuthMessage rm =
				messageParser.parseAuthMessage(m, clientHelper.toList(m));

		assertEquals(m.getId(), rm.getMessageId());
		assertEquals(m.getGroupId(), rm.getGroupId());
		assertEquals(m.getTimestamp(), rm.getTimestamp());
		assertEquals(previousMsgId, rm.getPreviousMessageId());
		assertEquals(sessionId, rm.getSessionId());
		assertArrayEquals(mac, rm.getMac());
		assertArrayEquals(signature, rm.getSignature());
	}

	@Test
	public void testActivateMessage() throws Exception {
		Message m = messageEncoder
				.encodeActivateMessage(groupId, timestamp, previousMsgId,
						sessionId);
		ActivateMessage rm =
				messageParser.parseActivateMessage(m, clientHelper.toList(m));

		assertEquals(m.getId(), rm.getMessageId());
		assertEquals(m.getGroupId(), rm.getGroupId());
		assertEquals(m.getTimestamp(), rm.getTimestamp());
		assertEquals(previousMsgId, rm.getPreviousMessageId());
		assertEquals(sessionId, rm.getSessionId());
	}

	@Test
	public void testAbortMessage() throws Exception {
		Message m = messageEncoder
				.encodeAbortMessage(groupId, timestamp, previousMsgId,
						sessionId);
		AbortMessage rm =
				messageParser.parseAbortMessage(m, clientHelper.toList(m));

		assertEquals(m.getId(), rm.getMessageId());
		assertEquals(m.getGroupId(), rm.getGroupId());
		assertEquals(m.getTimestamp(), rm.getTimestamp());
		assertEquals(previousMsgId, rm.getPreviousMessageId());
		assertEquals(sessionId, rm.getSessionId());
	}

}
