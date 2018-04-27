package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.ValidatorTestCase;
import org.briarproject.briar.api.client.SessionId;
import org.jmock.Expectations;
import org.junit.Test;

import javax.annotation.Nullable;

import static org.briarproject.bramble.api.crypto.CryptoConstants.MAC_BYTES;
import static org.briarproject.bramble.api.crypto.CryptoConstants.MAX_SIGNATURE_BYTES;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MAX_REQUEST_MESSAGE_LENGTH;
import static org.briarproject.briar.introduction.MessageType.ABORT;
import static org.briarproject.briar.introduction.MessageType.ACCEPT;
import static org.briarproject.briar.introduction.MessageType.ACTIVATE;
import static org.briarproject.briar.introduction.MessageType.AUTH;
import static org.briarproject.briar.introduction.MessageType.DECLINE;
import static org.briarproject.briar.introduction.MessageType.REQUEST;
import static org.junit.Assert.assertEquals;

public class IntroductionValidatorTest extends ValidatorTestCase {

	private final MessageEncoder messageEncoder =
			context.mock(MessageEncoder.class);
	private final IntroductionValidator validator =
			new IntroductionValidator(messageEncoder, clientHelper,
					metadataEncoder, clock);

	private final SessionId sessionId = new SessionId(getRandomId());
	private final MessageId previousMsgId = new MessageId(getRandomId());
	private final String text = getRandomString(MAX_REQUEST_MESSAGE_LENGTH);
	private final BdfDictionary meta = new BdfDictionary();
	private final long acceptTimestamp = 42;
	private final BdfDictionary transportProperties = BdfDictionary.of(
			new BdfEntry("transportId",  new BdfDictionary())
	);
	private final byte[] mac = getRandomBytes(MAC_BYTES);
	private final byte[] signature = getRandomBytes(MAX_SIGNATURE_BYTES);

	//
	// Introduction REQUEST
	//

	@Test
	public void testAcceptsRequest() throws Exception {
		BdfList body = BdfList.of(REQUEST.getValue(), previousMsgId.getBytes(),
				authorList, text);

		expectParseAuthor(authorList, author);
		expectEncodeRequestMetadata();
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);

		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test
	public void testAcceptsRequestWithPreviousMsgIdNull() throws Exception {
		BdfList body = BdfList.of(REQUEST.getValue(), null, authorList, text);

		expectParseAuthor(authorList, author);
		expectEncodeRequestMetadata();
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);

		assertExpectedContext(messageContext, null);
	}

	@Test
	public void testAcceptsRequestWithMessageNull() throws Exception {
		BdfList body = BdfList.of(REQUEST.getValue(), null, authorList, null);

		expectParseAuthor(authorList, author);
		expectEncodeRequestMetadata();
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);

		assertExpectedContext(messageContext, null);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBodyForRequest() throws Exception {
		BdfList body = BdfList.of(REQUEST.getValue(), null, authorList);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBodyForRequest() throws Exception {
		BdfList body =
				BdfList.of(REQUEST.getValue(), null, authorList, text, null);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsRawMessageForRequest() throws Exception {
		BdfList body =
				BdfList.of(REQUEST.getValue(), null, authorList, getRandomId());
		expectParseAuthor(authorList, author);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsStringMessageIdForRequest() throws Exception {
		BdfList body =
				BdfList.of(REQUEST.getValue(), "NoMessageId", authorList, null);
		validator.validateMessage(message, group, body);
	}

	//
	// Introduction ACCEPT
	//

	@Test
	public void testAcceptsAccept() throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), getRandomBytes(MAX_PUBLIC_KEY_LENGTH),
				acceptTimestamp, transportProperties);
		context.checking(new Expectations() {{
			oneOf(clientHelper).parseAndValidateTransportPropertiesMap(
					transportProperties);
		}});
		expectEncodeMetadata(ACCEPT);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);

		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBodyForAccept() throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(),
				getRandomBytes(MAX_PUBLIC_KEY_LENGTH), acceptTimestamp);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBodyForAccept() throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), getRandomBytes(MAX_PUBLIC_KEY_LENGTH),
				acceptTimestamp, transportProperties, null);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidSessionIdForAccept() throws Exception {
		BdfList body =
				BdfList.of(ACCEPT.getValue(), null, previousMsgId.getBytes(),
						getRandomBytes(MAX_PUBLIC_KEY_LENGTH), acceptTimestamp,
						transportProperties);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidPreviousMsgIdForAccept() throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(), 1,
				getRandomBytes(MAX_PUBLIC_KEY_LENGTH), acceptTimestamp,
				transportProperties);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongPublicKeyForAccept() throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(),
				getRandomBytes(MAX_PUBLIC_KEY_LENGTH + 1), acceptTimestamp,
				transportProperties);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNegativeTimestampForAccept() throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), getRandomBytes(MAX_PUBLIC_KEY_LENGTH),
				-1, transportProperties);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsEmptyTransportPropertiesForAccept()
			throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(),
				getRandomBytes(MAX_PUBLIC_KEY_LENGTH + 1), acceptTimestamp,
				new BdfDictionary());
		validator.validateMessage(message, group, body);
	}

	//
	// Introduction DECLINE
	//

	@Test
	public void testAcceptsDecline() throws Exception {
		BdfList body = BdfList.of(DECLINE.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes());

		expectEncodeMetadata(DECLINE);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);

		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBodyForDecline() throws Exception {
		BdfList body = BdfList.of(DECLINE.getValue(), sessionId.getBytes());
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBodyForDecline() throws Exception {
		BdfList body = BdfList.of(DECLINE.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), null);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidSessionIdForDecline() throws Exception {
		BdfList body =
				BdfList.of(DECLINE.getValue(), null, previousMsgId.getBytes());
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidPreviousMsgIdForDecline() throws Exception {
		BdfList body = BdfList.of(DECLINE.getValue(), sessionId.getBytes(), 1);
		validator.validateMessage(message, group, body);
	}

	//
	// Introduction AUTH
	//

	@Test
	public void testAcceptsAuth() throws Exception {
		BdfList body = BdfList.of(AUTH.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), mac, signature);

		expectEncodeMetadata(AUTH);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);

		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBodyForAuth() throws Exception {
		BdfList body = BdfList.of(AUTH.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), mac);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBodyForAuth() throws Exception {
		BdfList body = BdfList.of(AUTH.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), mac, signature, null);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidPreviousMsgIdForAuth() throws Exception {
		BdfList body = BdfList.of(AUTH.getValue(), sessionId.getBytes(),
				1, getRandomBytes(MAC_BYTES),
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPreviousMsgIdNullForAuth() throws Exception {
		BdfList body = BdfList.of(AUTH.getValue(), sessionId.getBytes(), null,
				getRandomBytes(MAC_BYTES), signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortMacForAuth() throws Exception {
		BdfList body = BdfList.of(AUTH.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), getRandomBytes(MAC_BYTES - 1),
				signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongMacForAuth() throws Exception {
		BdfList body = BdfList.of(AUTH.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(),
				getRandomBytes(MAC_BYTES + 1), signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidMacForAuth() throws Exception {
		BdfList body = BdfList.of(AUTH.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), null, signature);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortSignatureForAuth() throws Exception {
		BdfList body = BdfList.of(AUTH.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), mac, getRandomBytes(0));
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongSignatureForAuth() throws Exception {
		BdfList body = BdfList.of(AUTH.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), mac,
				getRandomBytes(MAX_SIGNATURE_BYTES + 1));
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidSignatureForAuth() throws Exception {
		BdfList body = BdfList.of(AUTH.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), mac, null);
		validator.validateMessage(message, group, body);
	}

	//
	// Introduction ACTIVATE
	//

	@Test
	public void testAcceptsActivate() throws Exception {
		BdfList body = BdfList.of(ACTIVATE.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), mac);

		expectEncodeMetadata(ACTIVATE);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);

		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBodyForActivate() throws Exception {
		BdfList body = BdfList.of(ACTIVATE.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes());
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBodyForActivate() throws Exception {
		BdfList body = BdfList.of(ACTIVATE.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), mac, null);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidSessionIdForActivate() throws Exception {
		BdfList body =
				BdfList.of(ACTIVATE.getValue(), null, previousMsgId.getBytes(),
						mac);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidPreviousMsgIdForActivate() throws Exception {
		BdfList body =
				BdfList.of(ACTIVATE.getValue(), sessionId.getBytes(), 1, mac);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsPreviousMsgIdNullForActivate() throws Exception {
		BdfList body =
				BdfList.of(ACTIVATE.getValue(), sessionId.getBytes(), null,
						mac);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidMacForActivate() throws Exception {
		BdfList body = BdfList.of(ACTIVATE.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), getRandomBytes(MAC_BYTES - 1));
		validator.validateMessage(message, group, body);
	}

	//
	// Introduction ABORT
	//

	@Test
	public void testAcceptsAbort() throws Exception {
		BdfList body = BdfList.of(ABORT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes());

		expectEncodeMetadata(ABORT);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);

		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBodyForAbort() throws Exception {
		BdfList body = BdfList.of(ABORT.getValue(), sessionId.getBytes());
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBodyForAbort() throws Exception {
		BdfList body = BdfList.of(ABORT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), null);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidSessionIdForAbort() throws Exception {
		BdfList body =
				BdfList.of(ABORT.getValue(), null, previousMsgId.getBytes());
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidPreviousMsgIdForAbort() throws Exception {
		BdfList body = BdfList.of(ABORT.getValue(), sessionId.getBytes(), 1);
		validator.validateMessage(message, group, body);
	}

	//
	// Introduction Helper Methods
	//

	private void expectEncodeRequestMetadata() {
		context.checking(new Expectations() {{
			oneOf(messageEncoder).encodeRequestMetadata(message.getTimestamp());
			will(returnValue(meta));
		}});
	}

	private void expectEncodeMetadata(MessageType type) {
		context.checking(new Expectations() {{
			oneOf(messageEncoder)
					.encodeMetadata(type, sessionId, message.getTimestamp(),
							false, false, false);
			will(returnValue(meta));
		}});
	}

	private void assertExpectedContext(BdfMessageContext c,
			@Nullable MessageId dependency) {
		assertEquals(meta, c.getDictionary());
		if (dependency == null) {
			assertEquals(0, c.getDependencies().size());
		} else {
			assertEquals(dependency, c.getDependencies().iterator().next());
		}
	}

}
