package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.system.SystemClock;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.test.BriarTestCase;
import org.jmock.Mockery;
import org.junit.Test;

import java.io.IOException;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.bramble.api.properties.TransportPropertyConstants.MAX_PROPERTY_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;
import static org.briarproject.briar.api.introduction.IntroductionConstants.ACCEPT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.E_PUBLIC_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.GROUP_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MAC;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MAC_LENGTH;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MAX_INTRODUCTION_MESSAGE_LENGTH;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MSG;
import static org.briarproject.briar.api.introduction.IntroductionConstants.NAME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.PUBLIC_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.SESSION_ID;
import static org.briarproject.briar.api.introduction.IntroductionConstants.SIGNATURE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TIME;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TRANSPORT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_ABORT;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_ACK;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_REQUEST;
import static org.briarproject.briar.api.introduction.IntroductionConstants.TYPE_RESPONSE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class IntroductionValidatorTest extends BriarTestCase {

	private final Mockery context = new Mockery();
	private final Group group;
	private final Message message;
	private final IntroductionValidator validator;
	private final Clock clock = new SystemClock();

	public IntroductionValidatorTest() {
		GroupId groupId = new GroupId(TestUtils.getRandomId());
		ClientId clientId = new ClientId(TestUtils.getRandomString(5));
		byte[] descriptor = TestUtils.getRandomBytes(12);
		group = new Group(groupId, clientId, descriptor);

		MessageId messageId = new MessageId(TestUtils.getRandomId());
		long timestamp = System.currentTimeMillis();
		byte[] raw = TestUtils.getRandomBytes(123);
		message = new Message(messageId, group.getId(), timestamp, raw);


		ClientHelper clientHelper = context.mock(ClientHelper.class);
		MetadataEncoder metadataEncoder = context.mock(MetadataEncoder.class);
		validator = new IntroductionValidator(clientHelper, metadataEncoder,
				clock);
		context.assertIsSatisfied();
	}

	//
	// Introduction Requests
	//

	@Test
	public void testValidateProperIntroductionRequest() throws IOException {
		final byte[] sessionId = TestUtils.getRandomId();
		final String name = TestUtils.getRandomString(MAX_AUTHOR_NAME_LENGTH);
		final byte[] publicKey =
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
		final String text =
				TestUtils.getRandomString(MAX_INTRODUCTION_MESSAGE_LENGTH);

		BdfList body = BdfList.of(TYPE_REQUEST, sessionId,
				name, publicKey, text);

		final BdfDictionary result =
				validator.validateMessage(message, group, body)
						.getDictionary();

		assertEquals(Long.valueOf(TYPE_REQUEST), result.getLong(TYPE));
		assertEquals(sessionId, result.getRaw(SESSION_ID));
		assertEquals(name, result.getString(NAME));
		assertEquals(publicKey, result.getRaw(PUBLIC_KEY));
		assertEquals(text, result.getString(MSG));
		context.assertIsSatisfied();
	}

	@Test(expected = FormatException.class)
	public void testValidateIntroductionRequestWithNoName() throws IOException {
		BdfDictionary msg = getValidIntroductionRequest();

		// no NAME is message
		BdfList body = BdfList.of(msg.getLong(TYPE), msg.getRaw(SESSION_ID),
				msg.getRaw(PUBLIC_KEY));
		if (msg.containsKey(MSG)) body.add(msg.getString(MSG));

		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testValidateIntroductionRequestWithLongName()
			throws IOException {
		// too long NAME in message
		BdfDictionary msg = getValidIntroductionRequest();
		msg.put(NAME, msg.get(NAME) + "x");
		BdfList body = BdfList.of(msg.getLong(TYPE), msg.getRaw(SESSION_ID),
				msg.getString(NAME), msg.getRaw(PUBLIC_KEY));
		if (msg.containsKey(MSG)) body.add(msg.getString(MSG));

		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testValidateIntroductionRequestWithWrongType()
			throws IOException {
		// wrong message type
		BdfDictionary msg = getValidIntroductionRequest();
		msg.put(TYPE, 324234);

		BdfList body = BdfList.of(msg.getLong(TYPE), msg.getRaw(SESSION_ID),
				msg.getString(NAME), msg.getRaw(PUBLIC_KEY));
		if (msg.containsKey(MSG)) body.add(msg.getString(MSG));
		validator.validateMessage(message, group, body);
	}

	private BdfDictionary getValidIntroductionRequest() throws FormatException {
		byte[] sessionId = TestUtils.getRandomId();
		String name = TestUtils.getRandomString(MAX_AUTHOR_NAME_LENGTH);
		byte[] publicKey = TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
		String text = TestUtils.getRandomString(MAX_MESSAGE_BODY_LENGTH);

		BdfDictionary msg = new BdfDictionary();
		msg.put(TYPE, TYPE_REQUEST);
		msg.put(SESSION_ID, sessionId);
		msg.put(NAME, name);
		msg.put(PUBLIC_KEY, publicKey);
		msg.put(MSG, text);

		return msg;
	}

	//
	// Introduction Responses
	//

	@Test
	public void testValidateIntroductionAcceptResponse() throws IOException {
		byte[] groupId = TestUtils.getRandomId();
		byte[] sessionId = TestUtils.getRandomId();
		long time = clock.currentTimeMillis();
		byte[] publicKey = TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
		String transportId = TestUtils
				.getRandomString(TransportId.MAX_TRANSPORT_ID_LENGTH);
		BdfDictionary tProps = BdfDictionary.of(
				new BdfEntry(TestUtils.getRandomString(MAX_PROPERTY_LENGTH),
						TestUtils.getRandomString(MAX_PROPERTY_LENGTH))
		);
		BdfDictionary tp = BdfDictionary.of(
				new BdfEntry(transportId, tProps)
		);

		BdfDictionary msg = new BdfDictionary();
		msg.put(TYPE, TYPE_RESPONSE);
		msg.put(GROUP_ID, groupId);
		msg.put(SESSION_ID, sessionId);
		msg.put(ACCEPT, true);
		msg.put(TIME, time);
		msg.put(E_PUBLIC_KEY, publicKey);
		msg.put(TRANSPORT, tp);

		BdfList body = BdfList.of(TYPE_RESPONSE, msg.getRaw(SESSION_ID),
				msg.getBoolean(ACCEPT), msg.getLong(TIME),
				msg.getRaw(E_PUBLIC_KEY), msg.getDictionary(TRANSPORT));

		final BdfDictionary result =
				validator.validateMessage(message, group, body).getDictionary();

		assertEquals(Long.valueOf(TYPE_RESPONSE), result.getLong(TYPE));
		assertEquals(sessionId, result.getRaw(SESSION_ID));
		assertEquals(true, result.getBoolean(ACCEPT));
		assertEquals(publicKey, result.getRaw(E_PUBLIC_KEY));
		assertEquals(tp, result.getDictionary(TRANSPORT));
		context.assertIsSatisfied();
	}

	@Test
	public void testValidateIntroductionDeclineResponse()
			throws IOException {
		BdfDictionary msg = getValidIntroductionResponse(false);
		BdfList body = BdfList.of(msg.getLong(TYPE), msg.getRaw(SESSION_ID),
				msg.getBoolean(ACCEPT));

		BdfDictionary result = validator.validateMessage(message, group, body)
				.getDictionary();

		assertFalse(result.getBoolean(ACCEPT));
		context.assertIsSatisfied();
	}

	@Test(expected = FormatException.class)
	public void testValidateIntroductionResponseWithoutAccept()
			throws IOException {
		BdfDictionary msg = getValidIntroductionResponse(false);
		BdfList body = BdfList.of(msg.getLong(TYPE), msg.getRaw(SESSION_ID));

		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testValidateIntroductionResponseWithBrokenTp()
			throws IOException {
		BdfDictionary msg = getValidIntroductionResponse(true);
		BdfDictionary tp = msg.getDictionary(TRANSPORT);
		tp.put(TestUtils
				.getRandomString(TransportId.MAX_TRANSPORT_ID_LENGTH), "X");
		msg.put(TRANSPORT, tp);

		BdfList body = BdfList.of(msg.getLong(TYPE), msg.getRaw(SESSION_ID),
				msg.getBoolean(ACCEPT), msg.getLong(TIME),
				msg.getRaw(E_PUBLIC_KEY), msg.getDictionary(TRANSPORT));

		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testValidateIntroductionResponseWithoutPublicKey()
			throws IOException {
		BdfDictionary msg = getValidIntroductionResponse(true);

		BdfList body = BdfList.of(msg.getLong(TYPE), msg.getRaw(SESSION_ID),
				msg.getBoolean(ACCEPT), msg.getLong(TIME),
				msg.getDictionary(TRANSPORT));

		validator.validateMessage(message, group, body);
	}

	private BdfDictionary getValidIntroductionResponse(boolean accept)
			throws FormatException {

		byte[] groupId = TestUtils.getRandomId();
		byte[] sessionId = TestUtils.getRandomId();
		long time = clock.currentTimeMillis();
		byte[] publicKey = TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
		String transportId = TestUtils
				.getRandomString(TransportId.MAX_TRANSPORT_ID_LENGTH);
		BdfDictionary tProps = BdfDictionary.of(
				new BdfEntry(TestUtils.getRandomString(MAX_PROPERTY_LENGTH),
						TestUtils.getRandomString(MAX_PROPERTY_LENGTH))
		);
		BdfDictionary tp = BdfDictionary.of(
				new BdfEntry(transportId, tProps)
		);

		BdfDictionary msg = new BdfDictionary();
		msg.put(TYPE, TYPE_RESPONSE);
		msg.put(GROUP_ID, groupId);
		msg.put(SESSION_ID, sessionId);
		msg.put(ACCEPT, accept);
		if (accept) {
			msg.put(TIME, time);
			msg.put(E_PUBLIC_KEY, publicKey);
			msg.put(TRANSPORT, tp);
		}

		return msg;
	}

	//
	// Introduction ACK
	//

	@Test
	public void testValidateProperIntroductionAck() throws IOException {
		byte[] sessionId = TestUtils.getRandomId();
		byte[] mac = TestUtils.getRandomBytes(MAC_LENGTH);
		byte[] sig = TestUtils.getRandomBytes(MAX_SIGNATURE_LENGTH);
		BdfList body = BdfList.of(TYPE_ACK, sessionId, mac, sig);

		BdfDictionary result =
				validator.validateMessage(message, group, body).getDictionary();

		assertEquals(Long.valueOf(TYPE_ACK), result.getLong(TYPE));
		assertArrayEquals(sessionId, result.getRaw(SESSION_ID));
		assertArrayEquals(mac, result.getRaw(MAC));
		assertArrayEquals(sig, result.getRaw(SIGNATURE));
		context.assertIsSatisfied();
	}

	@Test(expected = FormatException.class)
	public void testValidateTooLongIntroductionAck() throws IOException {
		BdfDictionary msg = BdfDictionary.of(
				new BdfEntry(TYPE, TYPE_ACK),
				new BdfEntry(SESSION_ID, TestUtils.getRandomId()),
				new BdfEntry("garbage", TestUtils.getRandomString(255))
		);
		BdfList body = BdfList.of(msg.getLong(TYPE), msg.getRaw(SESSION_ID),
				msg.getString("garbage"));

		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testValidateIntroductionAckWithLongSessionId()
			throws IOException {
		BdfDictionary msg = BdfDictionary.of(
				new BdfEntry(TYPE, TYPE_ACK),
				new BdfEntry(SESSION_ID, new byte[SessionId.LENGTH + 1])
		);
		BdfList body = BdfList.of(msg.getLong(TYPE), msg.getRaw(SESSION_ID));

		validator.validateMessage(message, group, body);
	}

	//
	// Introduction Abort
	//

	@Test
	public void testValidateProperIntroductionAbort() throws IOException {
		byte[] sessionId = TestUtils.getRandomId();

		BdfDictionary msg = new BdfDictionary();
		msg.put(TYPE, TYPE_ABORT);
		msg.put(SESSION_ID, sessionId);

		BdfList body = BdfList.of(msg.getLong(TYPE), msg.getRaw(SESSION_ID));

		BdfDictionary result =
				validator.validateMessage(message, group, body).getDictionary();

		assertEquals(Long.valueOf(TYPE_ABORT), result.getLong(TYPE));
		assertEquals(sessionId, result.getRaw(SESSION_ID));
		context.assertIsSatisfied();
	}

	@Test(expected = FormatException.class)
	public void testValidateTooLongIntroductionAbort() throws IOException {
		BdfDictionary msg = BdfDictionary.of(
				new BdfEntry(TYPE, TYPE_ABORT),
				new BdfEntry(SESSION_ID, TestUtils.getRandomId()),
				new BdfEntry("garbage", TestUtils.getRandomString(255))
		);
		BdfList body = BdfList.of(msg.getLong(TYPE), msg.getRaw(SESSION_ID),
				msg.getString("garbage"));

		validator.validateMessage(message, group, body);
	}

}
