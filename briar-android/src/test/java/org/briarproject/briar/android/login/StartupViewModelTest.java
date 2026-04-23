package org.briarproject.briar.android.login;

import android.app.Application;

import org.briarproject.bramble.api.FeatureFlags;
import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.telegram.TelegramAuthSession;
import org.briarproject.briar.api.telegram.TelegramAuthSession.RecoverableErrorDetail;
import org.briarproject.briar.api.telegram.TelegramAuthState;
import org.jmock.Expectations;
import org.jmock.imposters.ByteBuddyClassImposteriser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import static org.briarproject.briar.android.login.StartupViewModel.State.SIGNED_OUT;
import static org.briarproject.briar.android.login.StartupViewModel.State.TELEGRAM_LOGIN;
import static org.briarproject.briar.android.viewmodel.LiveDataTestUtil.getOrAwaitValue;
import static org.junit.Assert.assertEquals;

public class StartupViewModelTest extends BrambleMockTestCase {

	@Rule
	public final InstantTaskExecutorRule testRule =
			new InstantTaskExecutorRule();

	private StartupViewModel viewModel;
	private FakeTelegramAuthSession telegramAuthSession;

	@Before
	public void setUp() {
		context.setImposteriser(ByteBuddyClassImposteriser.INSTANCE);
		telegramAuthSession = new FakeTelegramAuthSession();
		Application app = context.mock(Application.class);
		AccountManager accountManager = context.mock(AccountManager.class);
		LifecycleManager lifecycleManager =
				context.mock(LifecycleManager.class);
		AndroidNotificationManager notificationManager =
				context.mock(AndroidNotificationManager.class);
		EventBus eventBus = context.mock(EventBus.class);
		SettingsManager settingsManager =
				context.mock(SettingsManager.class);
		FeatureFlags featureFlags = context.mock(FeatureFlags.class);

		context.checking(new Expectations() {{
			oneOf(lifecycleManager).getLifecycleState();
			will(returnValue(LifecycleState.STOPPED));
			oneOf(accountManager).hasDatabaseKey();
			will(returnValue(false));
			oneOf(eventBus).addListener(with(any(EventListener.class)));
		}});

		viewModel = new StartupViewModel(
				app,
				accountManager,
				lifecycleManager,
				notificationManager,
				eventBus,
				new ImmediateExecutor(),
				settingsManager,
				featureFlags,
				telegramAuthSession
		);
	}

	@Test
	public void testShowPasswordFragmentClearsTelegramIdentifierOnFallback()
			throws Exception {
		viewModel.setTelegramLoginIdentifier(" +123456789 ");
		viewModel.setTelegramLoginCode("12345");
		viewModel.setTelegramLoginPassword("secret");

		viewModel.showPasswordFragment();

		assertEquals("", viewModel.getTelegramLoginIdentifier());
		assertEquals("", viewModel.getTelegramLoginCode());
		assertEquals("", viewModel.getTelegramLoginPassword());
		assertEquals(TelegramAuthState.CLOSED,
				getOrAwaitValue(viewModel.getTelegramAuthState()));
		assertEquals(SIGNED_OUT, getOrAwaitValue(viewModel.getState()));
		assertEquals(1, telegramAuthSession.closeCalls);
	}

	@Test
	public void testShowPasswordFragmentClearsInvalidPasswordRecoverableErrorOnFallback()
			throws Exception {
		viewModel.setTelegramLoginIdentifier(" +123456789 ");
		viewModel.setTelegramLoginCode("12345");
		viewModel.setTelegramLoginPassword("secret");
		telegramAuthSession.recoverableErrorDetail =
				RecoverableErrorDetail.INVALID_PASSWORD;
		telegramAuthSession.currentState =
				TelegramAuthState.RECOVERABLE_ERROR;

		viewModel.showPasswordFragment();

		assertEquals("", viewModel.getTelegramLoginIdentifier());
		assertEquals("", viewModel.getTelegramLoginCode());
		assertEquals("", viewModel.getTelegramLoginPassword());
		assertEquals(TelegramAuthState.CLOSED,
				getOrAwaitValue(viewModel.getTelegramAuthState()));
		assertEquals(RecoverableErrorDetail.NONE,
				viewModel.getTelegramRecoverableErrorDetail());
		assertEquals(SIGNED_OUT, getOrAwaitValue(viewModel.getState()));
		assertEquals(1, telegramAuthSession.closeCalls);
	}

	@Test
	public void testShowTelegramLoginPlaceholderRestartsIdentifierEntryAfterFallback()
			throws Exception {
		viewModel.setTelegramLoginIdentifier(" +123456789 ");
		viewModel.setTelegramLoginCode("12345");
		viewModel.setTelegramLoginPassword("secret");
		telegramAuthSession.recoverableErrorDetail =
				RecoverableErrorDetail.INVALID_PASSWORD;
		telegramAuthSession.currentState =
				TelegramAuthState.RECOVERABLE_ERROR;

		viewModel.showPasswordFragment();
		viewModel.showTelegramLoginPlaceholder();

		assertEquals("", viewModel.getTelegramLoginIdentifier());
		assertEquals("", viewModel.getTelegramLoginCode());
		assertEquals("", viewModel.getTelegramLoginPassword());
		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY,
				getOrAwaitValue(viewModel.getTelegramAuthState()));
		assertEquals(RecoverableErrorDetail.NONE,
				viewModel.getTelegramRecoverableErrorDetail());
		assertEquals(TELEGRAM_LOGIN, getOrAwaitValue(viewModel.getState()));
		assertEquals(1, telegramAuthSession.closeCalls);
		assertEquals(1, telegramAuthSession.startCalls);
	}

	@Test
	public void testRetryTelegramLoginAfterStartTimeoutFallback() throws Exception {
		// Simulate a start() timeout fallback that sets RECOVERABLE_ERROR
		telegramAuthSession.currentState = TelegramAuthState.RECOVERABLE_ERROR;
		telegramAuthSession.recoverableErrorDetail = RecoverableErrorDetail.NONE;

		viewModel.showTelegramLoginPlaceholder();

		// Verify that the UI transitions to identifier entry after timeout fallback
		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY,
				getOrAwaitValue(viewModel.getTelegramAuthState()));
		assertEquals(RecoverableErrorDetail.NONE,
				viewModel.getTelegramRecoverableErrorDetail());
		assertEquals(TELEGRAM_LOGIN, getOrAwaitValue(viewModel.getState()));
		// Verify that start() is called to restart the login flow
		assertEquals(1, telegramAuthSession.startCalls);
		assertEquals(0, telegramAuthSession.closeCalls);

		// Verify that we can proceed with identifier submission after retry
		viewModel.setTelegramLoginIdentifier("+123456789");
		assertEquals(TelegramAuthState.IDENTIFIER_ENTRY,
				getOrAwaitValue(viewModel.getTelegramAuthState()));
	}

	private static class FakeTelegramAuthSession implements TelegramAuthSession {
		private TelegramAuthState currentState = TelegramAuthState.CLOSED;
		private RecoverableErrorDetail recoverableErrorDetail =
				RecoverableErrorDetail.NONE;
		private int closeCalls = 0;
		private int startCalls = 0;

		@Override
		public TelegramAuthState getCurrentState() {
			return currentState;
		}

		@Override
		public RecoverableErrorDetail getRecoverableErrorDetail() {
			return recoverableErrorDetail;
		}

		@Override
		public void start() {
			startCalls++;
			currentState = TelegramAuthState.IDENTIFIER_ENTRY;
		}

		@Override
		public void submitIdentifier(String identifier) {}

		@Override
		public void submitCode(String code) {}

		@Override
		public void submitPassword(String password) {}

		@Override
		public void close() {
			closeCalls++;
			currentState = TelegramAuthState.CLOSED;
			recoverableErrorDetail = RecoverableErrorDetail.NONE;
		}
	}
}
