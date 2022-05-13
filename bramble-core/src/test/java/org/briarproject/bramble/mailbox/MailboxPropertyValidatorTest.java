package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.mailbox.MailboxAuthToken;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxPropertiesUpdate;
import org.briarproject.bramble.api.mailbox.MailboxPropertiesUpdateMailbox;
import org.briarproject.bramble.api.mailbox.MailboxPropertyManager;
import org.briarproject.bramble.api.mailbox.MailboxVersion;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.junit.Assert.assertEquals;

public class MailboxPropertyValidatorTest extends BrambleMockTestCase {

	private final ClientHelper clientHelper = context.mock(ClientHelper.class);

	private final BdfDictionary bdfDict;
	private final BdfList emptyServerSupports;
	private final BdfList someClientSupports;
	private final List<MailboxVersion> someClientSupportsList;
	private final BdfList someServerSupports;
	private final MailboxPropertiesUpdateMailbox updateMailbox;
	private final MailboxPropertiesUpdate updateNoMailbox;
	private final Group group;
	private final Message message;
	private final MailboxPropertyValidator mpv;

	public MailboxPropertyValidatorTest() {
		// Just dummies, clientHelper is mocked so our test is a bit shallow;
		//  not testing
		//  {@link ClientHelper#parseAndValidateMailboxPropertiesUpdate(BdfDictionary)}
		emptyServerSupports = new BdfList();
		someClientSupports = BdfList.of(BdfList.of(1, 0));
		someClientSupportsList = singletonList(new MailboxVersion(1, 0));
		someServerSupports = BdfList.of(BdfList.of(1, 0));
		bdfDict = BdfDictionary.of(new BdfEntry("foo", "bar"));

		updateMailbox = new MailboxPropertiesUpdateMailbox(
				singletonList(new MailboxVersion(1, 0)),
				singletonList(new MailboxVersion(1, 0)),
				"baz",
				new MailboxAuthToken(getRandomId()),
				new MailboxFolderId(getRandomId()),
				new MailboxFolderId(getRandomId()));
		updateNoMailbox = new MailboxPropertiesUpdate(someClientSupportsList);


		group = getGroup(MailboxPropertyManager.CLIENT_ID,
				MailboxPropertyManager.MAJOR_VERSION);
		message = getMessage(group.getId());

		MetadataEncoder metadataEncoder = context.mock(MetadataEncoder.class);
		Clock clock = context.mock(Clock.class);
		mpv = new MailboxPropertyValidator(clientHelper, metadataEncoder,
				clock);
	}

	@Test
	public void testValidateMessageBody() throws IOException {
		BdfList body =
				BdfList.of(4, someClientSupports, someServerSupports, bdfDict);

		context.checking(new Expectations() {{
			oneOf(clientHelper).parseAndValidateMailboxPropertiesUpdate(
					someClientSupports, someServerSupports, bdfDict);
			will(returnValue(updateMailbox));
		}});

		BdfDictionary result =
				mpv.validateMessage(message, group, body).getDictionary();
		assertEquals(4, result.getLong("version").longValue());
	}

	@Test(expected = FormatException.class)
	public void testValidateWrongVersionValue() throws IOException {
		BdfList body = BdfList.of(-1, bdfDict);
		mpv.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testValidateWrongVersionType() throws IOException {
		BdfList body = BdfList.of(bdfDict, bdfDict);
		mpv.validateMessage(message, group, body);
	}

	@Test
	public void testEmptyProperties() throws IOException {
		BdfDictionary emptyBdfDict = new BdfDictionary();
		BdfList body = BdfList.of(42, someClientSupports, emptyServerSupports,
				emptyBdfDict);

		context.checking(new Expectations() {{
			oneOf(clientHelper).parseAndValidateMailboxPropertiesUpdate(
					someClientSupports, emptyServerSupports, emptyBdfDict);
			will(returnValue(updateNoMailbox));
		}});

		BdfDictionary result =
				mpv.validateMessage(message, group, body).getDictionary();
		assertEquals(42, result.getLong("version").longValue());
	}
}
