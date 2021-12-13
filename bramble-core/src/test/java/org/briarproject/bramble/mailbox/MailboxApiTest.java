package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.WeakSingletonProvider;
import org.briarproject.bramble.mailbox.MailboxApi.MailboxProperties;
import org.briarproject.bramble.mailbox.MailboxApi.PermanentFailureException;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.net.SocketFactory;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
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

	private final String token = getRandomString(64);
	private final String token2 = getRandomString(64);

	@Test
	public void testSetup() throws Exception {
		String validResponse = "{\"token\":\"" + token2 + "\"}";
		String invalidResponse = "{\"foo\":\"bar\"}";
		String invalidTokenResponse = "{\"token\":{\"foo\":\"bar\"}}";

		MockWebServer server = new MockWebServer();
		server.enqueue(new MockResponse().setBody(validResponse));
		server.enqueue(new MockResponse());
		server.enqueue(new MockResponse().setBody(invalidResponse));
		server.enqueue(new MockResponse().setResponseCode(401));
		server.enqueue(new MockResponse().setResponseCode(500));
		server.enqueue(new MockResponse().setBody(invalidTokenResponse));
		server.start();
		String baseUrl = getBaseUrl(server);
		MailboxProperties properties =
				new MailboxProperties(baseUrl, token, true);
		MailboxProperties properties2 =
				new MailboxProperties(baseUrl, token2, true);

		assertEquals(token2, api.setup(properties));

		PermanentFailureException e2 =
				assertThrows(PermanentFailureException.class,
						() -> api.setup(properties)
				);

		PermanentFailureException e3 =
				assertThrows(PermanentFailureException.class,
						() -> api.setup(properties)
				);

		PermanentFailureException e4 = assertThrows(
				PermanentFailureException.class,
				() -> api.setup(properties2)
		);

		assertThrows(IOException.class,
				() -> api.setup(properties)
		);

		PermanentFailureException e6 = assertThrows(
				PermanentFailureException.class,
				() -> api.setup(properties)
		);

		RecordedRequest request1 = server.takeRequest();
		assertEquals("/setup", request1.getPath());
		assertEquals("PUT", request1.getMethod());
		assertToken(request1, token);

		RecordedRequest request2 = server.takeRequest();
		assertEquals("/setup", request2.getPath());
		assertEquals("PUT", request2.getMethod());
		assertToken(request2, token);
		assertFalse(e2.fatal);

		RecordedRequest request3 = server.takeRequest();
		assertEquals("/setup", request3.getPath());
		assertEquals("PUT", request3.getMethod());
		assertToken(request3, token);
		assertFalse(e3.fatal);

		RecordedRequest request4 = server.takeRequest();
		assertEquals("/setup", request4.getPath());
		assertEquals("PUT", request4.getMethod());
		assertToken(request4, token2);
		assertTrue(e4.fatal);

		RecordedRequest request5 = server.takeRequest();
		assertEquals("/setup", request5.getPath());
		assertEquals("PUT", request5.getMethod());
		assertToken(request5, token);

		RecordedRequest request6 = server.takeRequest();
		assertEquals("/setup", request6.getPath());
		assertEquals("PUT", request6.getMethod());
		assertToken(request6, token);
		assertFalse(e6.fatal);
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

		PermanentFailureException e2 = assertThrows(
				PermanentFailureException.class,
				() -> api.checkStatus(properties2)
		);

		assertFalse(api.checkStatus(properties));

		RecordedRequest request1 = server.takeRequest();
		assertEquals("/status", request1.getPath());
		assertToken(request1, token);

		RecordedRequest request2 = server.takeRequest();
		assertEquals("/status", request2.getPath());
		assertToken(request2, token2);
		assertTrue(e2.fatal);

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

	private String getBaseUrl(MockWebServer server) {
		String baseUrl = server.url("").toString();
		return baseUrl.substring(0, baseUrl.length() - 1);
	}

	private void assertToken(RecordedRequest request, String token) {
		assertNotNull(request.getHeader("Authorization"));
		assertEquals("Bearer " + token, request.getHeader("Authorization"));
	}

}
