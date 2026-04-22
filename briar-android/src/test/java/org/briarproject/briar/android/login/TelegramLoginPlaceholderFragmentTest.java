package org.briarproject.briar.android.login;

import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import org.briarproject.bramble.api.FeatureFlags;
import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.telegram.TelegramAuthSession;
import org.briarproject.briar.api.telegram.TelegramAuthSession.RecoverableErrorDetail;
import org.briarproject.briar.api.telegram.TelegramAuthState;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import static android.os.Looper.getMainLooper;
import static org.briarproject.briar.android.login.StartupViewModel.State.SIGNED_OUT;
import static org.briarproject.briar.android.viewmodel.LiveDataTestUtil.getOrAwaitValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21)
public class TelegramLoginPlaceholderFragmentTest {

	@Rule
	public final InstantTaskExecutorRule testRule =
			new InstantTaskExecutorRule();

	private TestHostActivity activity;
	private StartupViewModel viewModel;
	private FakeTelegramAuthSession telegramAuthSession;

	@Before
	public void setUp() {
		telegramAuthSession = new FakeTelegramAuthSession();
		viewModel = createViewModel(telegramAuthSession);
		activity = Robolectric.buildActivity(TestHostActivity.class)
				.create()
				.start()
				.resume()
				.get();
		activity.setViewModel(viewModel);
		viewModel.showTelegramLoginPlaceholder();
		activity.showNextFragment(TelegramLoginPlaceholderFragment.newInstance());
		shadowOf(getMainLooper()).idle();
	}

	@Test
	public void testIdentifierContinueShowsCodeStepAndFallbackSignsOut()
			throws Exception {
		View identifierStep =
				activity.findViewById(R.id.telegram_login_identifier_step);
		View codeStep = activity.findViewById(R.id.telegram_login_code_step);
		Button continueButton =
				activity.findViewById(R.id.btn_telegram_login_continue);
		Button fallbackButton =
				activity.findViewById(R.id.btn_telegram_login_back);

		assertEquals(View.VISIBLE, identifierStep.getVisibility());
		assertEquals(View.GONE, codeStep.getVisibility());
		assertFalse(continueButton.isEnabled());

		telegramAuthSession.stateAfterSubmitIdentifier =
				TelegramAuthState.CODE_ENTRY;
			((android.widget.EditText) activity.findViewById(
					R.id.telegram_login_identifier)).setText(" +123456789 ");
		shadowOf(getMainLooper()).idle();

		assertTrue(continueButton.isEnabled());
		continueButton.performClick();
		shadowOf(getMainLooper()).idle();

		assertEquals("+123456789", telegramAuthSession.lastIdentifier);
		assertEquals(View.GONE, identifierStep.getVisibility());
		assertEquals(View.VISIBLE, codeStep.getVisibility());
		assertEquals(TelegramAuthState.CODE_ENTRY,
				getOrAwaitValue(viewModel.getTelegramAuthState()));

		fallbackButton.performClick();
		shadowOf(getMainLooper()).idle();

		assertEquals(1, telegramAuthSession.closeCalls);
		assertEquals(TelegramAuthState.CLOSED,
				getOrAwaitValue(viewModel.getTelegramAuthState()));
		assertEquals(SIGNED_OUT, getOrAwaitValue(viewModel.getState()));
	}


	@Test
	public void testConfirmationBackClearsStaleCodeFieldBeforeReopen()
			throws Exception {
		android.widget.EditText identifier = activity.findViewById(
				R.id.telegram_login_identifier);
		android.widget.EditText code =
				activity.findViewById(R.id.telegram_login_code);
		Button continueButton =
				activity.findViewById(R.id.btn_telegram_login_continue);
		Button codeContinueButton =
				activity.findViewById(R.id.btn_telegram_login_code_continue);
		Button confirmationBackButton = activity.findViewById(
				R.id.btn_telegram_login_confirmation_back);

		telegramAuthSession.stateAfterSubmitIdentifier =
				TelegramAuthState.CODE_ENTRY;
		identifier.setText("+123456789");
		shadowOf(getMainLooper()).idle();
		continueButton.performClick();
		shadowOf(getMainLooper()).idle();

		telegramAuthSession.stateAfterSubmitCode = TelegramAuthState.READY;
		code.setText("12345");
		shadowOf(getMainLooper()).idle();
		codeContinueButton.performClick();
		shadowOf(getMainLooper()).idle();

		confirmationBackButton.performClick();
		shadowOf(getMainLooper()).idle();

		telegramAuthSession.stateAfterSubmitIdentifier =
				TelegramAuthState.CODE_ENTRY;
		continueButton.performClick();
		shadowOf(getMainLooper()).idle();

		assertEquals("", code.getText().toString());
	}

	private static StartupViewModel createViewModel(
			TelegramAuthSession telegramAuthSession) {
		Application app = Mockito.mock(Application.class);
		AccountManager accountManager = Mockito.mock(AccountManager.class);
		LifecycleManager lifecycleManager =
				Mockito.mock(LifecycleManager.class);
		AndroidNotificationManager notificationManager =
				Mockito.mock(AndroidNotificationManager.class);
		EventBus eventBus = Mockito.mock(EventBus.class);
		SettingsManager settingsManager = Mockito.mock(SettingsManager.class);
		FeatureFlags featureFlags = Mockito.mock(FeatureFlags.class);

		when(lifecycleManager.getLifecycleState())
				.thenReturn(LifecycleState.STOPPED);
		when(accountManager.hasDatabaseKey()).thenReturn(false);

		return new StartupViewModel(
				app,
				accountManager,
				lifecycleManager,
				notificationManager,
				eventBus,
				Runnable::run,
				settingsManager,
				featureFlags,
				telegramAuthSession
		);
	}

	private static class FakeTelegramAuthSession implements TelegramAuthSession {
		private TelegramAuthState currentState = TelegramAuthState.CLOSED;
		private RecoverableErrorDetail recoverableErrorDetail =
				RecoverableErrorDetail.NONE;
		private TelegramAuthState stateAfterSubmitIdentifier =
				TelegramAuthState.IDENTIFIER_ENTRY;
		private TelegramAuthState stateAfterSubmitCode =
				TelegramAuthState.CODE_ENTRY;
		private String lastIdentifier = "";
		private int closeCalls = 0;

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
			currentState = TelegramAuthState.IDENTIFIER_ENTRY;
			recoverableErrorDetail = RecoverableErrorDetail.NONE;
		}

		@Override
		public void submitIdentifier(String identifier) {
			lastIdentifier = identifier;
			currentState = stateAfterSubmitIdentifier;
		}

		@Override
		public void submitCode(String code) {
			currentState = stateAfterSubmitCode;
		}

		@Override
		public void submitPassword(String password) {}

		@Override
		public void close() {
			closeCalls++;
			currentState = TelegramAuthState.CLOSED;
			recoverableErrorDetail = RecoverableErrorDetail.NONE;
		}
	}

	public static class TestHostActivity extends FragmentActivity
			implements BaseFragment.BaseFragmentListener {

		private ActivityComponent activityComponent;

		void setViewModel(StartupViewModel viewModel) {
			ViewModelProvider.Factory factory = new ViewModelProvider.Factory() {
				@Override
				public <T extends ViewModel> T create(Class<T> modelClass) {
					return modelClass.cast(viewModel);
				}
			};
			activityComponent = Mockito.mock(ActivityComponent.class);
			doAnswer(invocation -> {
				TelegramLoginPlaceholderFragment fragment = invocation.getArgument(0);
				fragment.viewModelFactory = factory;
				return null;
			}).when(activityComponent)
					.inject(any(TelegramLoginPlaceholderFragment.class));
		}

		@Override
		protected void onCreate(Bundle savedInstanceState) {
			setTheme(R.style.BriarTheme);
			super.onCreate(savedInstanceState);
			setContentView(R.layout.activity_fragment_container);
		}

		@Override
		public void runOnDbThread(Runnable runnable) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ActivityComponent getActivityComponent() {
			return activityComponent;
		}

		@Override
		public void showNextFragment(BaseFragment fragment) {
			getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.fragmentContainer, fragment, fragment.getUniqueTag())
					.commitNow();
		}

		@Override
		public void handleException(Exception e) {
			throw new RuntimeException(e);
		}
	}
}
