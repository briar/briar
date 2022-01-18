package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.WeakSingletonProvider;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.mailbox.MailboxApi.ApiException;
import org.briarproject.bramble.mailbox.MailboxApi.MailboxContact;
import org.briarproject.bramble.mailbox.MailboxApi.TolerableFailureException;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.net.SocketFactory;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.briarproject.bramble.test.TestUtils.getContactId;
import static org.briarproject.bramble.test.TestUtils.getMailboxSecret;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MailboxApiTest extends BrambleTestCase {

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

	private final String token = getMailboxSecret();
	private final String token2 = getMailboxSecret();
	private final ContactId contactId = getContactId();
	private final String contactToken = getMailboxSecret();
	private final String contactInboxId = getMailboxSecret();
	private final String contactOutboxId = getMailboxSecret();
	private final MailboxContact mailboxContact = new MailboxContact(
			contactId, contactToken, contactInboxId, contactOutboxId);

	@Test
	public void testSetup() throws Exception {
		String validResponse = "{\"token\":\"" + token2 + "\"}";
		String invalidResponse = "{\"foo\":\"bar\"}";
		String invalidTokenResponse = "{\"token\":{\"foo\":\"bar\"}}";
		String invalidTokenResponse2 =
				"{\"token\":\"" + getRandomString(64) + "\"}";

		MockWebServer server = new MockWebServer();
		server.enqueue(new MockResponse().setBody(validResponse));
		server.enqueue(new MockResponse());
		server.enqueue(new MockResponse().setBody(invalidResponse));
		server.enqueue(new MockResponse().setResponseCode(401));
		server.enqueue(new MockResponse().setResponseCode(500));
		server.enqueue(new MockResponse().setBody(invalidTokenResponse));
		server.enqueue(new MockResponse().setBody(invalidTokenResponse2));
		server.start();
		String baseUrl = getBaseUrl(server);
		MailboxProperties properties =
				new MailboxProperties(baseUrl, token, true);
		MailboxProperties properties2 =
				new MailboxProperties(baseUrl, token2, true);

		// valid response with valid token
		assertEquals(token2, api.setup(properties));
		RecordedRequest request1 = server.takeRequest();
		assertEquals("/setup", request1.getPath());
		assertEquals("PUT", request1.getMethod());
		assertToken(request1, token);

		// empty body
		assertThrows(ApiException.class, () -> api.setup(properties));
		RecordedRequest request2 = server.takeRequest();
		assertEquals("/setup", request2.getPath());
		assertEquals("PUT", request2.getMethod());
		assertToken(request2, token);

		// invalid response
		assertThrows(ApiException.class, () -> api.setup(properties));
		RecordedRequest request3 = server.takeRequest();
		assertEquals("/setup", request3.getPath());
		assertEquals("PUT", request3.getMethod());
		assertToken(request3, token);

		// 401 response
		assertThrows(ApiException.class, () -> api.setup(properties2));
		RecordedRequest request4 = server.takeRequest();
		assertEquals("/setup", request4.getPath());
		assertEquals("PUT", request4.getMethod());
		assertToken(request4, token2);

		// 500 response
		assertThrows(ApiException.class, () -> api.setup(properties));
		RecordedRequest request5 = server.takeRequest();
		assertEquals("/setup", request5.getPath());
		assertEquals("PUT", request5.getMethod());
		assertToken(request5, token);

		// invalid json dict token response
		assertThrows(ApiException.class, () -> api.setup(properties));
		RecordedRequest request6 = server.takeRequest();
		assertEquals("/setup", request6.getPath());
		assertEquals("PUT", request6.getMethod());
		assertToken(request6, token);

		// invalid non-hex string token response
		assertThrows(ApiException.class, () -> api.setup(properties));
		RecordedRequest request7 = server.takeRequest();
		assertEquals("/setup", request7.getPath());
		assertEquals("PUT", request7.getMethod());
		assertToken(request7, token);
	}

	@Test
	public void testSetupOnlyForOwner() {
		MailboxProperties properties =
				new MailboxProperties("", token, false);
		assertThrows(
				IllegalArgumentException.class,
				() -> api.setup(properties)
		);
	}

	@Test
	public void testStatus() throws Exception {
		MockWebServer server = new MockWebServer();
		server.enqueue(new MockResponse());
		server.enqueue(new MockResponse().setResponseCode(401));
		server.enqueue(new MockResponse().setResponseCode(500));
		server.start();
		String baseUrl = getBaseUrl(server);
		MailboxProperties properties =
				new MailboxProperties(baseUrl, token, true);
		MailboxProperties properties2 =
				new MailboxProperties(baseUrl, token2, true);

		assertTrue(api.checkStatus(properties));
		RecordedRequest request1 = server.takeRequest();
		assertEquals("/status", request1.getPath());
		assertToken(request1, token);

		assertThrows(ApiException.class, () -> api.checkStatus(properties2));
		RecordedRequest request2 = server.takeRequest();
		assertEquals("/status", request2.getPath());
		assertToken(request2, token2);

		assertFalse(api.checkStatus(properties));
		RecordedRequest request3 = server.takeRequest();
		assertEquals("/status", request3.getPath());
		assertToken(request3, token);
	}

	@Test
	public void testStatusOnlyForOwner() {
		MailboxProperties properties =
				new MailboxProperties("", token, false);
		assertThrows(
				IllegalArgumentException.class,
				() -> api.checkStatus(properties)
		);
	}

	@Test
	public void testAddContact() throws Exception {
		MockWebServer server = new MockWebServer();
		server.enqueue(new MockResponse());
		server.enqueue(new MockResponse().setResponseCode(401));
		server.enqueue(new MockResponse().setResponseCode(409));
		server.start();
		String baseUrl = getBaseUrl(server);
		MailboxProperties properties =
				new MailboxProperties(baseUrl, token, true);

		// contact gets added as expected
		api.addContact(properties, mailboxContact);
		RecordedRequest request1 = server.takeRequest();
		assertEquals("/contacts", request1.getPath());
		assertToken(request1, token);
		String expected = "{\"contactId\":" + contactId.getInt() +
				",\"token\":\"" + contactToken +
				"\",\"inboxId\":\"" + contactInboxId +
				"\",\"outboxId\":\"" + contactOutboxId +
				"\"}";
		assertEquals(expected, request1.getBody().readUtf8());

		// request is not successful
		assertThrows(ApiException.class, () ->
				api.addContact(properties, mailboxContact));
		RecordedRequest request2 = server.takeRequest();
		assertEquals("/contacts", request2.getPath());
		assertToken(request2, token);

		// contact already exists
		assertThrows(TolerableFailureException.class, () ->
				api.addContact(properties, mailboxContact));
		RecordedRequest request3 = server.takeRequest();
		assertEquals("/contacts", request3.getPath());
		assertToken(request3, token);
	}

	@Test
	public void testAddContactOnlyForOwner() {
		MailboxProperties properties =
				new MailboxProperties("", token, false);
		assertThrows(IllegalArgumentException.class, () ->
				api.addContact(properties, mailboxContact));
	}

	@Test
	public void testDeleteContact() throws Exception {
		MockWebServer server = new MockWebServer();
		server.enqueue(new MockResponse());
		server.enqueue(new MockResponse().setResponseCode(205));
		server.enqueue(new MockResponse().setResponseCode(401));
		server.enqueue(new MockResponse().setResponseCode(404));
		server.start();
		String baseUrl = getBaseUrl(server);
		MailboxProperties properties =
				new MailboxProperties(baseUrl, token, true);

		// contact gets deleted as expected
		api.deleteContact(properties, contactId);
		RecordedRequest request1 = server.takeRequest();
		assertEquals("DELETE", request1.getMethod());
		assertEquals("/contacts/" + contactId.getInt(), request1.getPath());
		assertToken(request1, token);

		// request is not returning 200
		assertThrows(ApiException.class, () ->
				api.deleteContact(properties, contactId));
		RecordedRequest request2 = server.takeRequest();
		assertEquals("DELETE", request2.getMethod());
		assertEquals("/contacts/" + contactId.getInt(), request2.getPath());
		assertToken(request2, token);

		// request is not authorized
		assertThrows(ApiException.class, () ->
				api.deleteContact(properties, contactId));
		RecordedRequest request3 = server.takeRequest();
		assertEquals("DELETE", request3.getMethod());
		assertEquals("/contacts/" + contactId.getInt(), request3.getPath());
		assertToken(request3, token);

		// tolerable 404 not found error
		assertThrows(TolerableFailureException.class,
				() -> api.deleteContact(properties, contactId));
		RecordedRequest request4 = server.takeRequest();
		assertEquals("/contacts/" + contactId.getInt(), request4.getPath());
		assertEquals("DELETE", request4.getMethod());
		assertToken(request4, token);
	}

	@Test
	public void testDeleteContactOnlyForOwner() {
		MailboxProperties properties =
				new MailboxProperties("", token, false);
		assertThrows(IllegalArgumentException.class, () ->
				api.deleteContact(properties, contactId));
	}

	@Test
	public void testGetContacts() throws Exception {
		ContactId contactId2 = getContactId();
		String validResponse1 = "{\"contacts\": [" + contactId.getInt() + "] }";
		String validResponse2 = "{\"contacts\": [" + contactId.getInt() + ", " +
				contactId2.getInt() + "] }";
		String invalidResponse1 = "{\"foo\":\"bar\"}";
		String invalidResponse2 = "{\"contacts\":{\"foo\":\"bar\"}}";
		String invalidResponse3 = "{\"contacts\": [1, 2, \"foo\"] }";

		MockWebServer server = new MockWebServer();
		server.enqueue(new MockResponse().setBody(validResponse1));
		server.enqueue(new MockResponse().setBody(validResponse2));
		server.enqueue(new MockResponse());
		server.enqueue(new MockResponse().setBody(invalidResponse1));
		server.enqueue(new MockResponse().setBody(invalidResponse2));
		server.enqueue(new MockResponse().setBody(invalidResponse3));
		server.enqueue(new MockResponse().setResponseCode(401));
		server.enqueue(new MockResponse().setResponseCode(500));
		server.start();
		String baseUrl = getBaseUrl(server);
		MailboxProperties properties =
				new MailboxProperties(baseUrl, token, true);

		// valid response with two contacts
		assertEquals(singletonList(contactId), api.getContacts(properties));
		RecordedRequest request1 = server.takeRequest();
		assertEquals("/contacts", request1.getPath());
		assertEquals("GET", request1.getMethod());
		assertToken(request1, token);

		// valid response with two contacts
		List<ContactId> contacts = new ArrayList<>();
		contacts.add(contactId);
		contacts.add(contactId2);
		assertEquals(contacts, api.getContacts(properties));
		RecordedRequest request2 = server.takeRequest();
		assertEquals("/contacts", request2.getPath());
		assertEquals("GET", request2.getMethod());
		assertToken(request2, token);

		// empty body
		assertThrows(ApiException.class, () -> api.getContacts(properties));
		RecordedRequest request3 = server.takeRequest();
		assertEquals("/contacts", request3.getPath());
		assertEquals("GET", request3.getMethod());
		assertToken(request3, token);

		// invalid response: no contacts key
		assertThrows(ApiException.class, () -> api.getContacts(properties));
		RecordedRequest request4 = server.takeRequest();
		assertEquals("/contacts", request4.getPath());
		assertEquals("GET", request4.getMethod());
		assertToken(request4, token);

		// invalid response: no list in contacts
		assertThrows(ApiException.class, () -> api.getContacts(properties));
		RecordedRequest request5 = server.takeRequest();
		assertEquals("/contacts", request5.getPath());
		assertEquals("GET", request5.getMethod());
		assertToken(request5, token);

		// invalid response: list with non-numbers
		assertThrows(ApiException.class, () -> api.getContacts(properties));
		RecordedRequest request6 = server.takeRequest();
		assertEquals("/contacts", request6.getPath());
		assertEquals("GET", request6.getMethod());
		assertToken(request6, token);

		// 401 not authorized
		assertThrows(ApiException.class, () -> api.getContacts(properties));
		RecordedRequest request7 = server.takeRequest();
		assertEquals("/contacts", request7.getPath());
		assertEquals("GET", request7.getMethod());
		assertToken(request7, token);

		// 500 internal server error
		assertThrows(ApiException.class, () -> api.getContacts(properties));
		RecordedRequest request8 = server.takeRequest();
		assertEquals("/contacts", request8.getPath());
		assertEquals("GET", request8.getMethod());
		assertToken(request8, token);
	}

	@Test
	public void testGetContactsOnlyForOwner() {
		MailboxProperties properties =
				new MailboxProperties("", token, false);
		assertThrows(
				IllegalArgumentException.class,
				() -> api.getContacts(properties)
		);
	}

	private String getBaseUrl(MockWebServer server) {
		String baseUrl = server.url("").toString();
		return baseUrl.substring(0, baseUrl.length() - 1);
	}

	private void assertToken(RecordedRequest request, String token) {
		assertNotNull(request.getHeader("Authorization"));
		assertEquals("Bearer " + token, request.getHeader("Authorization"));
	}

}
