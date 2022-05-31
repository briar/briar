package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxUpdate;
import org.briarproject.bramble.api.mailbox.MailboxUpdateManager;
import org.briarproject.bramble.api.mailbox.MailboxUpdateWithMailbox;
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
import static org.briarproject.bramble.test.TestUtils.getMailboxProperties;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.junit.Assert.assertEquals;

public class MailboxUpdateValidatorTest extends BrambleMockTestCase {

	private final ClientHelper clientHelper = context.mock(ClientHelper.class);

	private final BdfDictionary bdfDict;
	private final BdfList emptyServerSupports;
	private final BdfList someClientSupports;
	private final BdfList someServerSupports;
	private final MailboxUpdateWithMailbox updateMailbox;
	private final MailboxUpdate updateNoMailbox;
	private final Group group;
	private final Message message;
	private final MailboxUpdateValidator muv;

	public MailboxUpdateValidatorTest() {
		// Just dummies, clientHelper is mocked so our test is a bit shallow;
		//  not testing
		//  {@link ClientHelper#parseAndValidateMailboxUpdate(BdfList, BdfList, BdfDictionary)}
		emptyServerSupports = new BdfList();
		someClientSupports = BdfList.of(BdfList.of(1, 0));
		List<MailboxVersion> someClientSupportsList =
				singletonList(new MailboxVersion(1, 0));
		someServerSupports = BdfList.of(BdfList.of(1, 0));
		bdfDict = BdfDictionary.of(new BdfEntry("foo", "bar"));

		MailboxProperties props = getMailboxProperties(false,
				singletonList(new MailboxVersion(1, 0)));
		updateMailbox = new MailboxUpdateWithMailbox(
				singletonList(new MailboxVersion(1, 0)), props);
		updateNoMailbox = new MailboxUpdate(someClientSupportsList);


		group = getGroup(MailboxUpdateManager.CLIENT_ID,
				MailboxUpdateManager.MAJOR_VERSION);
		message = getMessage(group.getId());

		MetadataEncoder metadataEncoder = context.mock(MetadataEncoder.class);
		Clock clock = context.mock(Clock.class);
		muv = new MailboxUpdateValidator(clientHelper, metadataEncoder,
				clock);
	}

	@Test
	public void testValidateMessageBody() throws IOException {
		BdfList body =
				BdfList.of(4, someClientSupports, someServerSupports, bdfDict);

		context.checking(new Expectations() {{
			oneOf(clientHelper).parseAndValidateMailboxUpdate(
					someClientSupports, someServerSupports, bdfDict);
			will(returnValue(updateMailbox));
		}});

		BdfDictionary result =
				muv.validateMessage(message, group, body).getDictionary();
		assertEquals(4, result.getLong("version").longValue());
	}

	@Test(expected = FormatException.class)
	public void testValidateWrongVersionValue() throws IOException {
		BdfList body = BdfList.of(-1, bdfDict);
		muv.validateMessage(message, group, body);
	}

	@Test(expected = FormatException.class)
	public void testValidateWrongVersionType() throws IOException {
		BdfList body = BdfList.of(bdfDict, bdfDict);
		muv.validateMessage(message, group, body);
	}

	@Test
	public void testEmptyProperties() throws IOException {
		BdfDictionary emptyBdfDict = new BdfDictionary();
		BdfList body = BdfList.of(42, someClientSupports, emptyServerSupports,
				emptyBdfDict);

		context.checking(new Expectations() {{
			oneOf(clientHelper).parseAndValidateMailboxUpdate(
					someClientSupports, emptyServerSupports, emptyBdfDict);
			will(returnValue(updateNoMailbox));
		}});

		BdfDictionary result =
				muv.validateMessage(message, group, body).getDictionary();
		assertEquals(42, result.getLong("version").longValue());
	}
}
