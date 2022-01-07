package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.WeakSingletonProvider;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.mailbox.MailboxApi.ApiException;
import org.briarproject.bramble.mailbox.MailboxApi.MailboxContact;
import org.briarproject.bramble.mailbox.MailboxApi.TolerableFailureException;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import javax.annotation.Nonnull;
import javax.net.SocketFactory;

import okhttp3.OkHttpClient;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.briarproject.bramble.test.TestUtils.getMailboxSecret;
import static org.briarproject.bramble.test.TestUtils.isOptionalTestEnabled;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class MailboxIntegrationTest extends BrambleTestCase {

	private final static String URL_BASE = "http://127.0.0.1:8000";
	private final static String SETUP_TOKEN =
			"54686973206973206120736574757020746f6b656e20666f722042726961722e";

	private final OkHttpClient client = new OkHttpClient.Builder()
			.socketFactory(SocketFactory.getDefault())
			.connectTimeout(60_000, MILLISECONDS)
			.build();
	private final WeakSingletonProvider<OkHttpClient> httpClientProvider =
			new WeakSingletonProvider<OkHttpClient>() {
				@Override
				@Nonnull
				public OkHttpClient createInstance() {
					return client;
				}
			};
	private final MailboxApiImpl api = new MailboxApiImpl(httpClientProvider);
	// needs to be static to keep values across different tests
	private static MailboxProperties ownerProperties;

	/**
	 * Called before each test to make sure the mailbox is setup once
	 * before starting with individual tests.
	 * {@link BeforeClass} needs to be static, so we can't use the API class.
	 */
	@Before
	public void ensureSetup() throws IOException, ApiException {
		// Skip this test unless it's explicitly enabled in the environment
		assumeTrue(isOptionalTestEnabled(MailboxIntegrationTest.class));

		if (ownerProperties != null) return;
		MailboxProperties setupProperties =
				new MailboxProperties(URL_BASE, SETUP_TOKEN, true);
		String ownerToken = api.setup(setupProperties);
		ownerProperties = new MailboxProperties(URL_BASE, ownerToken, true);
	}

	@Test
	public void testStatus() throws Exception {
		assertTrue(api.checkStatus(ownerProperties));
	}

	@Test
	public void testContactApi() throws Exception {
		ContactId contactId1 = new ContactId(1);
		ContactId contactId2 = new ContactId(2);
		MailboxContact mailboxContact1 = getMailboxContact(contactId1);
		MailboxContact mailboxContact2 = getMailboxContact(contactId2);

		// no contacts initially
		assertEquals(emptyList(), api.getContacts(ownerProperties));
		// added contact gets returned
		api.addContact(ownerProperties, mailboxContact1);
		assertEquals(singletonList(contactId1),
				api.getContacts(ownerProperties));
		// second contact also gets returned
		api.addContact(ownerProperties, mailboxContact2);
		assertEquals(Arrays.asList(contactId1, contactId2),
				api.getContacts(ownerProperties));

		// after both contacts get deleted, the list is empty again
		api.deleteContact(ownerProperties, contactId1);
		api.deleteContact(ownerProperties, contactId2);
		assertEquals(emptyList(), api.getContacts(ownerProperties));

		// deleting again is tolerable
		assertThrows(TolerableFailureException.class,
				() -> api.deleteContact(ownerProperties, contactId2));
	}

	private MailboxContact getMailboxContact(ContactId contactId) {
		return new MailboxContact(contactId, getMailboxSecret(),
				getMailboxSecret(), getMailboxSecret());
	}

}
