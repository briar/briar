package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.ValidatorTestCase;
import org.briarproject.briar.api.client.SessionId;
import org.jmock.Expectations;
import org.junit.Test;

import java.util.Map;

import javax.annotation.Nullable;

import static java.util.Collections.singletonMap;
import static org.briarproject.bramble.api.crypto.CryptoConstants.MAC_BYTES;
import static org.briarproject.bramble.api.crypto.CryptoConstants.MAX_AGREEMENT_PUBLIC_KEY_BYTES;
import static org.briarproject.bramble.api.crypto.CryptoConstants.MAX_SIGNATURE_BYTES;
import static org.briarproject.bramble.test.TestUtils.getAgreementPublicKey;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getTransportId;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.MAX_AUTO_DELETE_TIMER_MS;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.MIN_AUTO_DELETE_TIMER_MS;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.briarproject.briar.api.introduction.IntroductionConstants.MAX_INTRODUCTION_TEXT_LENGTH;
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
	private final String text = getRandomString(MAX_INTRODUCTION_TEXT_LENGTH);
	private final BdfDictionary meta = new BdfDictionary();
	private final PublicKey ephemeralPublicKey = getAgreementPublicKey();
	private final long acceptTimestamp = 42;
	private final TransportId transportId = getTransportId();
	private final BdfDictionary transportProperties = BdfDictionary.of(
			new BdfEntry(transportId.getString(), new BdfDictionary())
	);
	private final Map<TransportId, TransportProperties> transportPropertiesMap =
			singletonMap(transportId, new TransportProperties());
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
		expectEncodeRequestMetadata(NO_AUTO_DELETE_TIMER);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);

		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test
	public void testAcceptsRequestWithPreviousMsgIdNull() throws Exception {
		BdfList body = BdfList.of(REQUEST.getValue(), null, authorList, text);

		expectParseAuthor(authorList, author);
		expectEncodeRequestMetadata(NO_AUTO_DELETE_TIMER);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);

		assertExpectedContext(messageContext, null);
	}

	@Test
	public void testAcceptsRequestWithMessageNull() throws Exception {
		BdfList body = BdfList.of(REQUEST.getValue(), null, authorList, null);

		expectParseAuthor(authorList, author);
		expectEncodeRequestMetadata(NO_AUTO_DELETE_TIMER);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);

		assertExpectedContext(messageContext, null);
	}

	@Test
	public void testAcceptsRequestWithNullAutoDeleteTimer() throws Exception {
		testAcceptsRequestWithAutoDeleteTimer(null);
	}

	@Test
	public void testAcceptsRequestWithMinAutoDeleteTimer() throws Exception {
		testAcceptsRequestWithAutoDeleteTimer(MIN_AUTO_DELETE_TIMER_MS);
	}

	@Test
	public void testAcceptsRequestWithMaxAutoDeleteTimer() throws Exception {
		testAcceptsRequestWithAutoDeleteTimer(MAX_AUTO_DELETE_TIMER_MS);
	}

	private void testAcceptsRequestWithAutoDeleteTimer(@Nullable Long timer)
			throws Exception {
		BdfList body = BdfList.of(REQUEST.getValue(), previousMsgId.getBytes(),
				authorList, text, timer);

		expectParseAuthor(authorList, author);
		long autoDeleteTimer = timer == null ? NO_AUTO_DELETE_TIMER : timer;
		expectEncodeRequestMetadata(autoDeleteTimer);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);

		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBodyForRequest() throws Exception {
		BdfList body = BdfList.of(REQUEST.getValue(), null, authorList);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBodyForRequest() throws Exception {
		BdfList body = BdfList.of(REQUEST.getValue(), null, authorList, text,
				null, null);
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

	@Test(expected = FormatException.class)
	public void testRejectsRequestWithNonLongAutoDeleteTimer()
			throws Exception {
		BdfList body = BdfList.of(REQUEST.getValue(), previousMsgId.getBytes(),
				authorList, text, "foo");
		expectParseAuthor(authorList, author);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsRequestWithTooSmallAutoDeleteTimer()
			throws Exception {
		BdfList body = BdfList.of(REQUEST.getValue(), previousMsgId.getBytes(),
				authorList, text, MIN_AUTO_DELETE_TIMER_MS - 1);
		expectParseAuthor(authorList, author);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsRequestWithTooBigAutoDeleteTimer() throws Exception {
		BdfList body = BdfList.of(REQUEST.getValue(), previousMsgId.getBytes(),
				authorList, text, MAX_AUTO_DELETE_TIMER_MS + 1);
		expectParseAuthor(authorList, author);
		validator.validateMessage(message, group, body);
	}

	//
	// Introduction ACCEPT
	//

	@Test
	public void testAcceptsAccept() throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), ephemeralPublicKey.getEncoded(),
				acceptTimestamp, transportProperties);
		expectParsePublicKey();
		expectParseTransportProperties();
		expectEncodeMetadata(ACCEPT, NO_AUTO_DELETE_TIMER);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);

		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test
	public void testAcceptsAcceptWithNullAutoDeleteTimer() throws Exception {
		testAcceptsAcceptWithAutoDeleteTimer(null);
	}

	@Test
	public void testAcceptsAcceptWithMinAutoDeleteTimer() throws Exception {
		testAcceptsAcceptWithAutoDeleteTimer(MIN_AUTO_DELETE_TIMER_MS);
	}

	@Test
	public void testAcceptsAcceptWithMaxAutoDeleteTimer() throws Exception {
		testAcceptsAcceptWithAutoDeleteTimer(MAX_AUTO_DELETE_TIMER_MS);
	}

	private void testAcceptsAcceptWithAutoDeleteTimer(@Nullable Long timer)
			throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), ephemeralPublicKey.getEncoded(),
				acceptTimestamp, transportProperties, timer);
		expectParsePublicKey();
		expectParseTransportProperties();
		long autoDeleteTimer = timer == null ? NO_AUTO_DELETE_TIMER : timer;
		expectEncodeMetadata(ACCEPT, autoDeleteTimer);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);

		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBodyForAccept() throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), ephemeralPublicKey.getEncoded(),
				acceptTimestamp);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBodyForAccept() throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), ephemeralPublicKey.getEncoded(),
				acceptTimestamp, transportProperties, null, null);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidSessionIdForAccept() throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), null,
				previousMsgId.getBytes(), ephemeralPublicKey.getEncoded(),
				acceptTimestamp, transportProperties);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsInvalidPreviousMsgIdForAccept() throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(), 1,
				ephemeralPublicKey.getEncoded(), acceptTimestamp,
				transportProperties);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongPublicKeyForAccept() throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(),
				getRandomBytes(MAX_AGREEMENT_PUBLIC_KEY_BYTES + 1),
				acceptTimestamp, transportProperties);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNegativeTimestampForAccept() throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), ephemeralPublicKey.getEncoded(),
				-1, transportProperties);
		expectParsePublicKey();
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsEmptyTransportPropertiesForAccept()
			throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), ephemeralPublicKey.getEncoded(),
				acceptTimestamp, new BdfDictionary());
		expectParsePublicKey();
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAcceptWithNonLongAutoDeleteTimer()
			throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), ephemeralPublicKey.getEncoded(),
				acceptTimestamp, transportProperties, "foo");
		expectParsePublicKey();
		expectParseTransportProperties();
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAcceptWithTooSmallAutoDeleteTimer()
			throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), ephemeralPublicKey.getEncoded(),
				acceptTimestamp, transportProperties,
				MIN_AUTO_DELETE_TIMER_MS - 1);
		expectParsePublicKey();
		expectParseTransportProperties();
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsAcceptWithTooBigAutoDeleteTimer() throws Exception {
		BdfList body = BdfList.of(ACCEPT.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), ephemeralPublicKey.getEncoded(),
				acceptTimestamp, transportProperties,
				MAX_AUTO_DELETE_TIMER_MS + 1);
		expectParsePublicKey();
		expectParseTransportProperties();
		validator.validateMessage(message, group, body);
	}

	//
	// Introduction DECLINE
	//

	@Test
	public void testAcceptsDecline() throws Exception {
		BdfList body = BdfList.of(DECLINE.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes());

		expectEncodeMetadata(DECLINE, NO_AUTO_DELETE_TIMER);
		BdfMessageContext messageContext =
				validator.validateMessage(message, group, body);

		assertExpectedContext(messageContext, previousMsgId);
	}

	@Test
	public void testAcceptsDeclineWithNullAutoDeleteTimer() throws Exception {
		testAcceptsDeclineWithAutoDeleteTimer(null);
	}

	@Test
	public void testAcceptsDeclineWithMinAutoDeleteTimer() throws Exception {
		testAcceptsDeclineWithAutoDeleteTimer(MIN_AUTO_DELETE_TIMER_MS);
	}

	@Test
	public void testAcceptsDeclineWithMaxAutoDeleteTimer() throws Exception {
		testAcceptsDeclineWithAutoDeleteTimer(MAX_AUTO_DELETE_TIMER_MS);
	}

	private void testAcceptsDeclineWithAutoDeleteTimer(@Nullable Long timer)
			throws Exception {
		BdfList body = BdfList.of(DECLINE.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), timer);

		long autoDeleteTimer = timer == null ? NO_AUTO_DELETE_TIMER : timer;
		expectEncodeMetadata(DECLINE, autoDeleteTimer);
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
				previousMsgId.getBytes(), null, null);
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

	@Test(expected = FormatException.class)
	public void testRejectsNonLongAutoDeleteTimerForDecline() throws Exception {
		BdfList body = BdfList.of(DECLINE.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), "foo");
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooSmallAutoDeleteTimerForDecline()
			throws Exception {
		BdfList body = BdfList.of(DECLINE.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), MIN_AUTO_DELETE_TIMER_MS - 1);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooBigAutoDeleteTimerForDecline() throws Exception {
		BdfList body = BdfList.of(DECLINE.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), MAX_AUTO_DELETE_TIMER_MS + 1);
		validator.validateMessage(message, group, body);
	}

	//
	// Introduction AUTH
	//

	@Test
	public void testAcceptsAuth() throws Exception {
		BdfList body = BdfList.of(AUTH.getValue(), sessionId.getBytes(),
				previousMsgId.getBytes(), mac, signature);

		expectEncodeMetadata(AUTH, NO_AUTO_DELETE_TIMER);
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
				1, getRandomBytes(MAC_BYTES), signature);
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
				previousMsgId.getBytes(), getRandomBytes(MAC_BYTES + 1),
				signature);
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

		expectEncodeMetadata(ACTIVATE, NO_AUTO_DELETE_TIMER);
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
		BdfList body = BdfList.of(ACTIVATE.getValue(), null,
				previousMsgId.getBytes(), mac);
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
		BdfList body = BdfList.of(ACTIVATE.getValue(), sessionId.getBytes(),
				null, mac);
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

		expectEncodeMetadata(ABORT, NO_AUTO_DELETE_TIMER);
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

	private void expectParsePublicKey() throws Exception {
		context.checking(new Expectations() {{
			oneOf(clientHelper).parseAndValidateAgreementPublicKey(
					ephemeralPublicKey.getEncoded());
			will(returnValue(ephemeralPublicKey));
		}});
	}

	private void expectParseTransportProperties() throws Exception {
		context.checking(new Expectations() {{
			oneOf(clientHelper).parseAndValidateTransportPropertiesMap(
					transportProperties);
			will(returnValue(transportPropertiesMap));
		}});
	}

	private void expectEncodeRequestMetadata(long autoDeleteTimer) {
		context.checking(new Expectations() {{
			oneOf(messageEncoder).encodeRequestMetadata(message.getTimestamp(),
					autoDeleteTimer);
			will(returnValue(meta));
		}});
	}

	private void expectEncodeMetadata(MessageType type, long autoDeleteTimer) {
		context.checking(new Expectations() {{
			oneOf(messageEncoder).encodeMetadata(type, sessionId,
					message.getTimestamp(), autoDeleteTimer);
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
