package org.briarproject.bramble.versioning;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.junit.Test;

import static java.util.Collections.emptyList;
import static org.briarproject.bramble.api.sync.ClientId.MAX_CLIENT_ID_LENGTH;
import static org.briarproject.bramble.api.versioning.ClientVersioningManager.CLIENT_ID;
import static org.briarproject.bramble.api.versioning.ClientVersioningManager.MAJOR_VERSION;
import static org.briarproject.bramble.test.TestUtils.getClientId;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.bramble.versioning.ClientVersioningConstants.MSG_KEY_LOCAL;
import static org.briarproject.bramble.versioning.ClientVersioningConstants.MSG_KEY_UPDATE_VERSION;
import static org.junit.Assert.assertEquals;

public class ClientVersioningValidatorTest extends BrambleMockTestCase {

	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final MetadataEncoder metadataEncoder =
			context.mock(MetadataEncoder.class);
	private final Clock clock = context.mock(Clock.class);
	private final ClientVersioningValidator validator =
			new ClientVersioningValidator(clientHelper, metadataEncoder, clock);

	private final Group group = getGroup(CLIENT_ID, MAJOR_VERSION);
	private final Message message = getMessage(group.getId());
	private final ClientId clientId = getClientId();

	@Test(expected = FormatException.class)
	public void testRejectsTooShortBody() throws Exception {
		BdfList body = BdfList.of(new BdfList());
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongBody() throws Exception {
		BdfList body = BdfList.of(new BdfList(), 123, null);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullStatesList() throws Exception {
		BdfList body = BdfList.of(null, 123);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonListStatesList() throws Exception {
		BdfList body = BdfList.of("", 123);
		validator.validateMessage(message, group, body);
	}

	@Test
	public void testAcceptsEmptyStatesList() throws Exception {
		BdfList body = BdfList.of(new BdfList(), 123);
		BdfMessageContext context =
				validator.validateMessage(message, group, body);
		assertEquals(emptyList(), context.getDependencies());
		BdfDictionary expectedMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 123L),
				new BdfEntry(MSG_KEY_LOCAL, false));
		assertEquals(expectedMeta, context.getDictionary());
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullUpdateVersion() throws Exception {
		BdfList body = BdfList.of(new BdfList(), null);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonLongUpdateVersion() throws Exception {
		BdfList body = BdfList.of(new BdfList(), "123");
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNegativeUpdateVersion() throws Exception {
		BdfList body = BdfList.of(new BdfList(), -1);
		validator.validateMessage(message, group, body);
	}

	@Test
	public void testAcceptsZeroUpdateVersion() throws Exception {
		BdfList body = BdfList.of(new BdfList(), 0);
		BdfMessageContext context =
				validator.validateMessage(message, group, body);
		assertEquals(emptyList(), context.getDependencies());
		BdfDictionary expectedMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 0L),
				new BdfEntry(MSG_KEY_LOCAL, false));
		assertEquals(expectedMeta, context.getDictionary());
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortClientState() throws Exception {
		BdfList state = BdfList.of(clientId.getString(), 123, 234);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongClientState() throws Exception {
		BdfList state = BdfList.of(clientId.getString(), 123, 234, true, null);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullClientId() throws Exception {
		BdfList state = BdfList.of(null, 123, 234, true);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonStringClientId() throws Exception {
		byte[] id = getRandomBytes(MAX_CLIENT_ID_LENGTH);
		BdfList state = BdfList.of(id, 123, 234, true);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooShortClientId() throws Exception {
		BdfList state = BdfList.of("", 123, 234, true);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		validator.validateMessage(message, group, body);
	}

	@Test
	public void testAcceptsMinLengthClientId() throws Exception {
		BdfList state = BdfList.of(getRandomString(1), 123, 234, true);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		BdfMessageContext context =
				validator.validateMessage(message, group, body);
		assertEquals(emptyList(), context.getDependencies());
		BdfDictionary expectedMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 345L),
				new BdfEntry(MSG_KEY_LOCAL, false));
		assertEquals(expectedMeta, context.getDictionary());
	}

	@Test(expected = FormatException.class)
	public void testRejectsTooLongClientId() throws Exception {
		String id = getRandomString(MAX_CLIENT_ID_LENGTH + 1);
		BdfList state = BdfList.of(id, 123, 234, true);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		validator.validateMessage(message, group, body);
	}

	@Test
	public void testAcceptsMaxLengthClientId() throws Exception {
		String id = getRandomString(MAX_CLIENT_ID_LENGTH);
		BdfList state = BdfList.of(id, 123, 234, true);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		BdfMessageContext context =
				validator.validateMessage(message, group, body);
		assertEquals(emptyList(), context.getDependencies());
		BdfDictionary expectedMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 345L),
				new BdfEntry(MSG_KEY_LOCAL, false));
		assertEquals(expectedMeta, context.getDictionary());
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullMajorVersion() throws Exception {
		BdfList state = BdfList.of(clientId.getString(), null, 234, true);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonLongMajorVersion() throws Exception {
		BdfList state = BdfList.of(clientId.getString(), "123", 234, true);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNegativeMajorVersion() throws Exception {
		BdfList state = BdfList.of(clientId.getString(), -1, 234, true);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		validator.validateMessage(message, group, body);
	}

	@Test
	public void testAcceptsZeroMajorVersion() throws Exception {
		BdfList state = BdfList.of(clientId.getString(), 0, 234, true);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		BdfMessageContext context =
				validator.validateMessage(message, group, body);
		assertEquals(emptyList(), context.getDependencies());
		BdfDictionary expectedMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 345L),
				new BdfEntry(MSG_KEY_LOCAL, false));
		assertEquals(expectedMeta, context.getDictionary());
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullMinorVersion() throws Exception {
		BdfList state = BdfList.of(clientId.getString(), 123, null, true);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonLongMinorVersion() throws Exception {
		BdfList state = BdfList.of(clientId.getString(), 123, "234", true);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNegativeMinorVersion() throws Exception {
		BdfList state = BdfList.of(clientId.getString(), 123, -1, true);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		validator.validateMessage(message, group, body);
	}

	@Test
	public void testAcceptsZeroMinorVersion() throws Exception {
		BdfList state = BdfList.of(clientId.getString(), 123, 0, true);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		BdfMessageContext context =
				validator.validateMessage(message, group, body);
		assertEquals(emptyList(), context.getDependencies());
		BdfDictionary expectedMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 345L),
				new BdfEntry(MSG_KEY_LOCAL, false));
		assertEquals(expectedMeta, context.getDictionary());
	}

	@Test(expected = FormatException.class)
	public void testRejectsNullActiveFlag() throws Exception {
		BdfList state = BdfList.of(clientId.getString(), 123, 234, null);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		validator.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testRejectsNonBooleanActiveFlag() throws Exception {
		BdfList state = BdfList.of(clientId.getString(), 123, 234, "true");
		BdfList body = BdfList.of(BdfList.of(state), 345);
		validator.validateMessage(message, group, body);
	}

	@Test
	public void testAcceptsNegativeActiveFlag() throws Exception {
		BdfList state = BdfList.of(clientId.getString(), 123, 234, false);
		BdfList body = BdfList.of(BdfList.of(state), 345);
		BdfMessageContext context =
				validator.validateMessage(message, group, body);
		assertEquals(emptyList(), context.getDependencies());
		BdfDictionary expectedMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 345L),
				new BdfEntry(MSG_KEY_LOCAL, false));
		assertEquals(expectedMeta, context.getDictionary());
	}
}
