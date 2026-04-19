package org.briarproject.briar.telegram;

import org.briarproject.briar.api.telegram.TelegramAuthSession.RecoverableErrorDetail;
import org.briarproject.briar.api.telegram.TelegramAuthState;
import org.drinkless.tdlib.Client;
import org.junit.After;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class StubTelegramTdlibLoginClientTest {

	@After
	public void tearDown() {
		Client.resetTestState();
	}

	@Test
	public void testStartThenSubmitIdentifierTransitionsToCodeEntry() {
		StubTelegramTdlibLoginClient client = new StubTelegramTdlibLoginClient();

		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start());
		assertEquals(RecoverableErrorDetail.NONE,
				client.getRecoverableErrorDetail());

		assertEquals(TelegramAuthState.CODE_ENTRY,
				client.submitIdentifier("+123456789"));
		assertEquals(RecoverableErrorDetail.NONE,
				client.getRecoverableErrorDetail());
		assertEquals(Arrays.asList("SetTdlibParameters",
				"SetAuthenticationPhoneNumber"), Client.getSentRequestNames());
		assertEquals("+123456789", Client.getLastPhoneNumber());

		client.close();
	}

	@Test
	public void testSubmitInvalidIdentifierReturnsRecoverableError() {
		StubTelegramTdlibLoginClient client = new StubTelegramTdlibLoginClient();

		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start());
		assertEquals(TelegramAuthState.RECOVERABLE_ERROR,
				client.submitIdentifier("invalid-phone"));
		assertEquals(RecoverableErrorDetail.INVALID_IDENTIFIER,
				client.getRecoverableErrorDetail());
		assertEquals(Arrays.asList("SetTdlibParameters",
				"SetAuthenticationPhoneNumber"), Client.getSentRequestNames());
		assertEquals("invalid-phone", Client.getLastPhoneNumber());

		client.close();
	}
}
