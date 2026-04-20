package org.briarproject.briar.android.login

import android.app.Application
import org.briarproject.bramble.api.FeatureFlags
import org.briarproject.bramble.api.account.AccountManager
import org.briarproject.bramble.api.event.EventBus
import org.briarproject.bramble.api.event.EventListener
import org.briarproject.bramble.api.lifecycle.LifecycleManager
import org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState
import org.briarproject.bramble.api.settings.SettingsManager
import org.briarproject.bramble.test.BrambleMockTestCase
import org.briarproject.bramble.test.ImmediateExecutor
import org.briarproject.briar.api.android.AndroidNotificationManager
import org.briarproject.briar.api.telegram.TelegramAuthSession
import org.briarproject.briar.api.telegram.TelegramAuthSession.RecoverableErrorDetail
import org.briarproject.briar.api.telegram.TelegramAuthState
import org.jmock.Expectations
import org.jmock.imposters.ByteBuddyClassImposteriser
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.briarproject.briar.android.login.StartupViewModel.State.SIGNED_OUT
import org.briarproject.briar.android.viewmodel.LiveDataTestUtil.getOrAwaitValue
import org.junit.Assert.assertEquals

class StartupViewModelTest : BrambleMockTestCase() {

	@get:Rule
	val testRule = InstantTaskExecutorRule()

	private lateinit var viewModel: StartupViewModel
	private lateinit var telegramAuthSession: FakeTelegramAuthSession

	override fun setUp() {
		context.setImposteriser(ByteBuddyClassImposteriser.INSTANCE)
		telegramAuthSession = FakeTelegramAuthSession()
		val app = context.mock(Application::class.java)
		val accountManager = context.mock(AccountManager::class.java)
		val lifecycleManager = context.mock(LifecycleManager::class.java)
		val notificationManager = context.mock(AndroidNotificationManager::class.java)
		val eventBus = context.mock(EventBus::class.java)
		val settingsManager = context.mock(SettingsManager::class.java)
		val featureFlags = context.mock(FeatureFlags::class.java)

		context.checking(object : Expectations() {{
			oneOf(lifecycleManager).lifecycleState
			will(returnValue(LifecycleState.STOPPED))
			oneOf(accountManager).hasDatabaseKey
			will(returnValue(false))
			oneOf(eventBus).addListener(with(any(EventListener::class.java)))
		}})

		viewModel = StartupViewModel(
			app, accountManager, lifecycleManager,
			notificationManager, eventBus, ImmediateExecutor(),
			settingsManager, featureFlags, telegramAuthSession
		)
	}

	@Test
	fun `testShowPasswordFragmentClearsTelegramIdentifierOnFallback`() {
		viewModel.setTelegramLoginIdentifier(" +123456789 ")
		viewModel.setTelegramLoginCode("12345")
		viewModel.setTelegramLoginPassword("secret")

		viewModel.showPasswordFragment()

		assertEquals("", viewModel.telegramLoginIdentifier)
		assertEquals("", viewModel.telegramLoginCode)
		assertEquals("", viewModel.telegramLoginPassword)
		assertEquals(TelegramAuthState.CLOSED, getOrAwaitValue(viewModel.telegramAuthState))
		assertEquals(SIGNED_OUT, getOrAwaitValue(viewModel.state))
		assertEquals(1, telegramAuthSession.closeCalls)
	}

	@Test
	fun `testCloseAfterInvalidPasswordClearsRecoverableErrorAndAllowsRestart`() {
		// Setup: configure session to return INVALID_PASSWORD error
		viewModel.setTelegramLoginIdentifier(" +123456789 ")
		viewModel.setTelegramLoginCode("12345")
		viewModel.setTelegramLoginPassword("secret")
		telegramAuthSession.recoverableErrorDetail = RecoverableErrorDetail.INVALID_PASSWORD
		telegramAuthSession.currentState = TelegramAuthState.RECOVERABLE_ERROR

		// When: user calls showPasswordFragment for Harbor password fallback
		viewModel.showPasswordFragment()

		// Then: all staged Telegram auth data is cleared
		assertEquals("", viewModel.telegramLoginIdentifier)
		assertEquals("", viewModel.telegramLoginCode)
		assertEquals("", viewModel.telegramLoginPassword)
		// Session is closed and state resets
		assertEquals(TelegramAuthState.CLOSED, getOrAwaitValue(viewModel.telegramAuthState))
		assertEquals(SIGNED_OUT, getOrAwaitValue(viewModel.state))
		assertEquals(1, telegramAuthSession.closeCalls)
		// Session can be restarted (simulated by calling start again)
		telegramAuthSession.closeCalls = 0
		viewModel.showTelegramLoginPlaceholder()
		assertEquals(1, telegramAuthSession.startCalls)
	}

	private class FakeTelegramAuthSession : TelegramAuthSession {
		var currentState: TelegramAuthState = TelegramAuthState.CLOSED
			private set
		var recoverableErrorDetail: RecoverableErrorDetail = RecoverableErrorDetail.NONE
		var closeCalls = 0
		var startCalls = 0

		override fun getCurrentState(): TelegramAuthState = currentState
		override fun getRecoverableErrorDetail(): RecoverableErrorDetail = recoverableErrorDetail
		override fun start() {
			startCalls++
			currentState = TelegramAuthState.IDENTIFIER_ENTRY
		}
		override fun submitIdentifier(identifier: String) {}
		override fun submitCode(code: String) {}
		override fun submitPassword(password: String) {}
		override fun close() {
			closeCalls++
			currentState = TelegramAuthState.CLOSED
		}
	}
}
