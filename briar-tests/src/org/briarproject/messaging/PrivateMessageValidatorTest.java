package org.briarproject.messaging;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.FormatException;
import org.briarproject.api.clients.BdfMessageContext;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Test;

import static org.briarproject.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH;
import static org.briarproject.clients.BdfConstants.MSG_KEY_READ;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PrivateMessageValidatorTest extends BriarTestCase {

	private final Mockery context = new Mockery();
	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final MetadataEncoder metadataEncoder =
			context.mock(MetadataEncoder.class);
	private final Clock clock = context.mock(Clock.class);

	private final MessageId messageId = new MessageId(TestUtils.getRandomId());
	private final GroupId groupId = new GroupId(TestUtils.getRandomId());
	private final long timestamp = 1234567890 * 1000L;
	private final byte[] raw = TestUtils.getRandomBytes(123);
	private final Message message =
			new Message(messageId, groupId, timestamp, raw);
	private final ClientId clientId =
			new ClientId(TestUtils.getRandomString(123));
	private final byte[] descriptor = TestUtils.getRandomBytes(123);
	private final Group group = new Group(groupId, clientId, descriptor);

	@After
	public void checkExpectations() {
		context.assertIsSatisfied();
	}

	@Test(expected = FormatException.class)
	public void testRejectsEmptyBody() throws Exception {
		PrivateMessageValidator v = new PrivateMessageValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group, new BdfList());
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBody() throws Exception {
		PrivateMessageValidator v = new PrivateMessageValidator(clientHelper,
				metadataEncoder, clock);
		v.validateMessage(message, group, BdfList.of("", ""));
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
		String content =
				TestUtils.getRandomString(MAX_PRIVATE_MESSAGE_BODY_LENGTH + 1);
		v.validateMessage(message, group, BdfList.of(content));
	}

	@Test
	public void testAcceptsMaxLengthContent() throws Exception {
		PrivateMessageValidator v = new PrivateMessageValidator(clientHelper,
				metadataEncoder, clock);
		String content =
				TestUtils.getRandomString(MAX_PRIVATE_MESSAGE_BODY_LENGTH);
		BdfMessageContext context =
				v.validateMessage(message, group, BdfList.of(content));
		assertExpectedContext(context);
	}

	@Test
	public void testAcceptsEmptyContent() throws Exception {
		PrivateMessageValidator v = new PrivateMessageValidator(clientHelper,
				metadataEncoder, clock);
		BdfMessageContext context =
				v.validateMessage(message, group, BdfList.of(""));
		assertExpectedContext(context);
	}

	private void assertExpectedContext(BdfMessageContext context)
			throws FormatException {
		assertEquals(0, context.getDependencies().size());
		BdfDictionary meta = context.getDictionary();
		assertEquals(3, meta.size());
		assertEquals(Long.valueOf(timestamp), meta.getLong("timestamp"));
		assertFalse(meta.getBoolean("local"));
		assertFalse(meta.getBoolean(MSG_KEY_READ));
	}
}
