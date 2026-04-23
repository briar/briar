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
	public void testStartWaitsForBriefDelayedAuthorizationUpdate() {
		Client.setAuthorizationUpdateDelayMs(300L);
		StubTelegramTdlibLoginClient client = new StubTelegramTdlibLoginClient();

		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start());
		assertEquals(RecoverableErrorDetail.NONE,
				client.getRecoverableErrorDetail());

		client.close();
	}

	@Test
	public void testSubmitIdentifierWaitsForBriefDelayedAuthorizationUpdate() {
		Client.setAuthorizationUpdateDelaySequenceMs(0L, 0L, 300L);
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

	@Test
	public void testSubmitInvalidCodeReturnsRecoverableError() {
		StubTelegramTdlibLoginClient client = new StubTelegramTdlibLoginClient();

		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start());
		assertEquals(TelegramAuthState.CODE_ENTRY,
				client.submitIdentifier("+123456789"));
		assertEquals(TelegramAuthState.RECOVERABLE_ERROR,
				client.submitCode("invalid-code"));
		assertEquals(RecoverableErrorDetail.INVALID_CODE,
				client.getRecoverableErrorDetail());
		assertEquals(Arrays.asList("SetTdlibParameters",
				"SetAuthenticationPhoneNumber",
				"CheckAuthenticationCode"), Client.getSentRequestNames());

		client.close();
	}

	@Test
	public void testSubmitCodeTransitionsToReady() {
		StubTelegramTdlibLoginClient client = new StubTelegramTdlibLoginClient();

		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start());
		assertEquals(TelegramAuthState.CODE_ENTRY,
				client.submitIdentifier("+123456789"));
		assertEquals(TelegramAuthState.READY, client.submitCode("12345"));
		assertEquals(RecoverableErrorDetail.NONE,
				client.getRecoverableErrorDetail());
		assertEquals(Arrays.asList("SetTdlibParameters",
				"SetAuthenticationPhoneNumber",
				"CheckAuthenticationCode"), Client.getSentRequestNames());

		client.close();
	}

	@Test
	public void testSubmitCodeWaitsForBriefDelayedReadyAuthorizationUpdate() {
		Client.setAuthorizationUpdateDelaySequenceMs(0L, 0L, 0L, 300L);
		StubTelegramTdlibLoginClient client = new StubTelegramTdlibLoginClient();

		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start());
		assertEquals(TelegramAuthState.CODE_ENTRY,
				client.submitIdentifier("+123456789"));
		assertEquals(TelegramAuthState.READY, client.submitCode("12345"));
		assertEquals(RecoverableErrorDetail.NONE,
				client.getRecoverableErrorDetail());
		assertEquals(Arrays.asList("SetTdlibParameters",
				"SetAuthenticationPhoneNumber",
				"CheckAuthenticationCode"), Client.getSentRequestNames());

		client.close();
	}

	@Test
	public void testSubmitCodeWaitsForBriefDelayedPasswordAuthorizationUpdate() {
		Client.setAuthorizationUpdateDelaySequenceMs(0L, 0L, 0L, 300L);
		StubTelegramTdlibLoginClient client = new StubTelegramTdlibLoginClient();

		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start());
		assertEquals(TelegramAuthState.CODE_ENTRY,
				client.submitIdentifier("+123456789"));
		assertEquals(TelegramAuthState.PASSWORD_ENTRY,
				client.submitCode("password-required"));
		assertEquals(RecoverableErrorDetail.NONE,
				client.getRecoverableErrorDetail());
		assertEquals(Arrays.asList("SetTdlibParameters",
				"SetAuthenticationPhoneNumber",
				"CheckAuthenticationCode"), Client.getSentRequestNames());

		client.close();
	}

	@Test
	public void testSubmitCodeTransitionsToPasswordEntry() {
		StubTelegramTdlibLoginClient client = new StubTelegramTdlibLoginClient();

		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start());
		assertEquals(TelegramAuthState.CODE_ENTRY,
				client.submitIdentifier("+123456789"));
		assertEquals(TelegramAuthState.PASSWORD_ENTRY,
				client.submitCode("password-required"));
		assertEquals(RecoverableErrorDetail.NONE,
				client.getRecoverableErrorDetail());
		assertEquals(Arrays.asList("SetTdlibParameters",
				"SetAuthenticationPhoneNumber",
				"CheckAuthenticationCode"), Client.getSentRequestNames());

		client.close();
	}

	@Test
	public void testSubmitPasswordTransitionsToReady() {
		StubTelegramTdlibLoginClient client = new StubTelegramTdlibLoginClient();

		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start());
		assertEquals(TelegramAuthState.CODE_ENTRY,
				client.submitIdentifier("+123456789"));
		assertEquals(TelegramAuthState.PASSWORD_ENTRY,
				client.submitCode("password-required"));
		assertEquals(TelegramAuthState.READY,
				client.submitPassword("hunter2"));
		assertEquals(RecoverableErrorDetail.NONE,
				client.getRecoverableErrorDetail());
		assertEquals(Arrays.asList("SetTdlibParameters",
				"SetAuthenticationPhoneNumber",
				"CheckAuthenticationCode",
				"CheckAuthenticationPassword"), Client.getSentRequestNames());

		client.close();
	}

	@Test
	public void testStartIgnoresDelayedReadyUpdateFromClosedPasswordSession()
			throws Exception {
		StubTelegramTdlibLoginClient client = new StubTelegramTdlibLoginClient();
		TelegramAuthState[] delayedPasswordResult = new TelegramAuthState[1];

		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start());
		assertEquals(TelegramAuthState.CODE_ENTRY,
				client.submitIdentifier("+123456789"));
		assertEquals(TelegramAuthState.PASSWORD_ENTRY,
				client.submitCode("password-required"));

		Client.setAuthorizationUpdateDelaySequenceMs(300L, 0L, 400L);
		Thread submitPasswordThread = new Thread(() ->
				delayedPasswordResult[0] = client.submitPassword("hunter2"));
		submitPasswordThread.start();

		Thread.sleep(50L);

		assertEquals(TelegramAuthState.CLOSED, client.close());
		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start());
		assertEquals(RecoverableErrorDetail.NONE,
				client.getRecoverableErrorDetail());

		submitPasswordThread.join();
		assertEquals(TelegramAuthState.CLOSED, delayedPasswordResult[0]);
		assertEquals(Arrays.asList("SetTdlibParameters",
				"SetAuthenticationPhoneNumber",
				"CheckAuthenticationCode",
				"CheckAuthenticationPassword",
				"Close"), Client.getSentRequestNames());

		client.close();
	}

	@Test
	public void testSubmitInvalidPasswordReturnsRecoverableErrorAndAllowsRetry() {
		StubTelegramTdlibLoginClient client = new StubTelegramTdlibLoginClient();

		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start());
		assertEquals(TelegramAuthState.CODE_ENTRY,
				client.submitIdentifier("+123456789"));
		assertEquals(TelegramAuthState.PASSWORD_ENTRY,
				client.submitCode("password-required"));
		assertEquals(TelegramAuthState.RECOVERABLE_ERROR,
				client.submitPassword("invalid-password"));
		assertEquals(RecoverableErrorDetail.INVALID_PASSWORD,
				client.getRecoverableErrorDetail());
		assertEquals(Arrays.asList("SetTdlibParameters",
				"SetAuthenticationPhoneNumber",
				"CheckAuthenticationCode",
				"CheckAuthenticationPassword"), Client.getSentRequestNames());

		assertEquals(TelegramAuthState.READY,
				client.submitPassword("hunter2"));
		assertEquals(RecoverableErrorDetail.NONE,
				client.getRecoverableErrorDetail());
		assertEquals(Arrays.asList("SetTdlibParameters",
				"SetAuthenticationPhoneNumber",
				"CheckAuthenticationCode",
				"CheckAuthenticationPassword",
				"CheckAuthenticationPassword"), Client.getSentRequestNames());

		client.close();
	}

	@Test
	public void testSubmitPasswordWaitsForBriefDelayedReadyAuthorizationUpdate() {
		Client.setAuthorizationUpdateDelaySequenceMs(0L, 0L, 0L, 0L, 300L);
		StubTelegramTdlibLoginClient client = new StubTelegramTdlibLoginClient();

		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start());
		assertEquals(TelegramAuthState.CODE_ENTRY,
				client.submitIdentifier("+123456789"));
		assertEquals(TelegramAuthState.PASSWORD_ENTRY,
				client.submitCode("password-required"));
		assertEquals(TelegramAuthState.READY,
				client.submitPassword("hunter2"));
		assertEquals(RecoverableErrorDetail.NONE,
				client.getRecoverableErrorDetail());
		assertEquals(Arrays.asList("SetTdlibParameters",
				"SetAuthenticationPhoneNumber",
				"CheckAuthenticationCode",
				"CheckAuthenticationPassword"), Client.getSentRequestNames());

		client.close();
	}

	@Test
	public void testCloseAfterInvalidPasswordClearsRecoverableErrorAndAllowsRestart() {
		StubTelegramTdlibLoginClient client = new StubTelegramTdlibLoginClient();

		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start());
		assertEquals(TelegramAuthState.CODE_ENTRY,
				client.submitIdentifier("+123456789"));
		assertEquals(TelegramAuthState.PASSWORD_ENTRY,
				client.submitCode("password-required"));
		assertEquals(TelegramAuthState.RECOVERABLE_ERROR,
				client.submitPassword("invalid-password"));
		assertEquals(RecoverableErrorDetail.INVALID_PASSWORD,
				client.getRecoverableErrorDetail());

		assertEquals(TelegramAuthState.CLOSED, client.close());
		assertEquals(RecoverableErrorDetail.NONE,
				client.getRecoverableErrorDetail());
		assertEquals(Arrays.asList("SetTdlibParameters",
				"SetAuthenticationPhoneNumber",
				"CheckAuthenticationCode",
				"CheckAuthenticationPassword",
				"Close"), Client.getSentRequestNames());

		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start());
		assertEquals(RecoverableErrorDetail.NONE,
				client.getRecoverableErrorDetail());
		assertEquals(TelegramAuthState.CODE_ENTRY,
				client.submitIdentifier("+123456789"));
		assertEquals(RecoverableErrorDetail.NONE,
				client.getRecoverableErrorDetail());
		assertEquals(Arrays.asList("SetTdlibParameters",
				"SetAuthenticationPhoneNumber",
				"CheckAuthenticationCode",
				"CheckAuthenticationPassword",
				"Close",
				"SetTdlibParameters",
				"SetAuthenticationPhoneNumber"), Client.getSentRequestNames());

		client.close();
	}
}
