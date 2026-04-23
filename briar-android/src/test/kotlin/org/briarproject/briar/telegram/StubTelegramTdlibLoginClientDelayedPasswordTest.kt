package org.briarproject.briar.telegram

import org.briarproject.briar.api.telegram.TelegramAuthState
import org.drinkless.tdlib.Client
import org.junit.After
import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * Tests for delayed post-password ready authorization updates on the non-close path.
 * These tests ensure submitPassword() properly handles delayed success updates.
 */
class StubTelegramTdlibLoginClientDelayedPasswordTest {

	@After
	fun tearDown() {
		Client.resetTestState()
	}

	/**
	 * Tests that submitPassword() waits for a delayed authorization update
	 * and correctly transitions to READY state when the update eventually arrives.
	 * This verifies the non-close path where the password submission succeeds
	 * but the authorization state update is delayed.
	 */
	@Test
	fun testSubmitPasswordWaitsForDelayedSuccessAuthorizationUpdate() {
		// Set up a delay sequence:
		// - 0ms for start (WaitTdlibParameters -> WaitPhoneNumber)
		// - 0ms for submitIdentifier (WaitPhoneNumber -> WaitCode)
		// - 0ms for submitCode (WaitCode -> WaitPassword)
		// - 0ms for submitPassword request to be sent
		// - 500ms delay for the password-ready update to arrive
		Client.setAuthorizationUpdateDelaySequenceMs(0L, 0L, 0L, 0L, 500L)

		val client = StubTelegramTdlibLoginClient()

		// Start the login flow
		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start())
		assertEquals(RecoverableErrorDetail.NONE, client.getRecoverableErrorDetail())

		assertEquals(TelegramAuthState.CODE_ENTRY, client.submitIdentifier("+123456789"))
		assertEquals(RecoverableErrorDetail.NONE, client.getRecoverableErrorDetail())

		assertEquals(TelegramAuthState.PASSWORD_ENTRY, client.submitCode("password-required"))
		assertEquals(RecoverableErrorDetail.NONE, client.getRecoverableErrorDetail())

		// Submit password - this should wait for the delayed authorization update
		// and transition to READY state
		val startTime = System.currentTimeMillis()
		val result = client.submitPassword("hunter2")
		val elapsed = System.currentTimeMillis() - startTime

		assertEquals(TelegramAuthState.READY, result)
		assertEquals(RecoverableErrorDetail.NONE, client.getRecoverableErrorDetail())

		// Verify that we waited for the delayed update (should be at least 400ms
		// to account for the 500ms delay plus some tolerance)
		assert(elapsed >= 400) { "Expected to wait for delayed update, but only waited ${elapsed}ms" }

		// Verify the correct requests were sent
		assertEquals(
			listOf("SetTdlibParameters", "SetAuthenticationPhoneNumber",
				"CheckAuthenticationCode", "CheckAuthenticationPassword"),
			Client.getSentRequestNames()
		)
		assertEquals("hunter2", Client.getLastPassword())

		client.close()
	}

	/**
	 * Tests that multiple sequential submitPassword() calls with delayed updates
	 * are properly handled, ensuring each call waits for its corresponding
	 * authorization update.
	 */
	@Test
	fun testSequentialSubmitPasswordWithDelayedUpdates() {
		// First password attempt with delay
		Client.setAuthorizationUpdateDelaySequenceMs(0L, 0L, 0L, 0L, 300L)

		val client = StubTelegramTdlibLoginClient()

		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start())
		assertEquals(TelegramAuthState.CODE_ENTRY, client.submitIdentifier("+123456789"))
		assertEquals(TelegramAuthState.PASSWORD_ENTRY, client.submitCode("password-required"))

		// First password attempt - delayed success
		val start1 = System.currentTimeMillis()
		val result1 = client.submitPassword("password1")
		val elapsed1 = System.currentTimeMillis() - start1

		assertEquals(TelegramAuthState.READY, result1)
		assert(elapsed1 >= 200) { "First attempt should wait for delayed update, waited ${elapsed1}ms" }

		// Second password attempt - immediate success
		Client.setAuthorizationUpdateDelaySequenceMs(0L, 0L, 0L, 0L, 0L, 0L)
		val start2 = System.currentTimeMillis()
		val result2 = client.submitPassword("password2")
		val elapsed2 = System.currentTimeMillis() - start2

		assertEquals(TelegramAuthState.READY, result2)
		assert(elapsed2 < 100) { "Second attempt should be fast, took ${elapsed2}ms" }

		client.close()
	}

	/**
	 * Tests that a moderate delay on password update doesn't cause timeout
	 * or false error states.
	 */
	@Test
	fun testSubmitPasswordWithModerateDelay() {
		// 800ms delay is within the AUTHORIZATION_UPDATE_TIMEOUT_MS (1000ms)
		Client.setAuthorizationUpdateDelaySequenceMs(0L, 0L, 0L, 0L, 800L)

		val client = StubTelegramTdlibLoginClient()

		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY, client.start())
		assertEquals(TelegramAuthState.CODE_ENTRY, client.submitIdentifier("+123456789"))
		assertEquals(TelegramAuthState.PASSWORD_ENTRY, client.submitCode("password-required"))

		val startTime = System.currentTimeMillis()
		val result = client.submitPassword("correct-password")
		val elapsed = System.currentTimeMillis() - startTime

		assertEquals(TelegramAuthState.READY, result)
		assertEquals(RecoverableErrorDetail.NONE, client.getRecoverableErrorDetail())

		// Should have waited for the delay (800ms) plus some tolerance
		assert(elapsed >= 700 && elapsed < 1500) {
			"Expected to wait ~800ms for delayed update, but waited ${elapsed}ms"
		}

		client.close()
	}
}
