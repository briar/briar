package org.briarproject.briar.messaging;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.test.ValidatorTestCase;
import org.junit.Test;

import static org.briarproject.briar.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH;
import static org.briarproject.briar.client.MessageTrackerConstants.MSG_KEY_READ;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PrivateMessageValidatorTest extends ValidatorTestCase {

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBody() throws Exception {
		PrivateMessageValidator v = new PrivateMessageValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group, new BdfList());
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBody() throws Exception {
		PrivateMessageValidator v = new PrivateMessageValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group, BdfList.of("", 123));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullContent() throws Exception {
		PrivateMessageValidator v = new PrivateMessageValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group, BdfList.of((String) null));
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonStringContent() throws Exception {
		PrivateMessageValidator v = new PrivateMessageValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group, BdfList.of(123));
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongContent() throws Exception {
		PrivateMessageValidator v = new PrivateMessageValidator(clientHelper,
				metadataEncoder, clock);
		String invalidContent =
				TestUtils.getRandomString(MAX_PRIVATE_MESSAGE_BODY_LENGTH + 1);
		v.validateMessage(message, group, BdfList.of(invalidContent));
	}

	@Test
	public void testAcceptsMaxLengthContent() throws Exception {
		PrivateMessageValidator v = new PrivateMessageValidator(clientHelper,
				metadataEncoder, clock);
		String content =
				TestUtils.getRandomString(MAX_PRIVATE_MESSAGE_BODY_LENGTH);
		BdfMessageContext messageContext =
				v.validateMessage(message, group, BdfList.of(content));
		assertExpectedContext(messageContext);
	}

	@Test
	public void testAcceptsMinLengthContent() throws Exception {
		PrivateMessageValidator v = new PrivateMessageValidator(clientHelper,
				metadataEncoder, clock);
		BdfMessageContext messageContext =
				v.validateMessage(message, group, BdfList.of(""));
		assertExpectedContext(messageContext);
	}

	private void assertExpectedContext(BdfMessageContext messageContext)
			throws FormatException {
		BdfDictionary meta = messageContext.getDictionary();
		assertEquals(3, meta.size());
		assertEquals(timestamp, meta.getLong("timestamp").longValue());
		assertFalse(meta.getBoolean("local"));
		assertFalse(meta.getBoolean(MSG_KEY_READ));
		assertEquals(0, messageContext.getDependencies().size());
	}
}
