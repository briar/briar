package org.briarproject.briar.android.login;

import android.app.Application;

import org.briarproject.bramble.api.FeatureFlags;
import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.crypto.DecryptionException;
import org.briarproject.bramble.api.crypto.DecryptionResult;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState;
import org.briarproject.bramble.api.lifecycle.event.LifecycleEvent;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.briar.android.viewmodel.LiveEvent;
import org.briarproject.briar.android.viewmodel.MutableLiveEvent;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.telegram.TelegramAuthSession;
import org.briarproject.briar.api.telegram.TelegramAuthSession.RecoverableErrorDetail;
import org.briarproject.briar.api.telegram.TelegramAuthState;
import org.briarproject.nullsafety.NotNullByDefault;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.UiThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static org.briarproject.bramble.api.crypto.DecryptionResult.SUCCESS;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.COMPACTING_DATABASE;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.MIGRATING_DATABASE;
import static org.briarproject.bramble.api.lifecycle.LifecycleManager.LifecycleState.STARTING_SERVICES;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.login.StartupViewModel.State.COMPACTING;
import static org.briarproject.briar.android.login.StartupViewModel.State.MIGRATING;
import static org.briarproject.briar.android.login.StartupViewModel.State.SIGNED_IN;
import static org.briarproject.briar.android.login.StartupViewModel.State.SIGNED_OUT;
import static org.briarproject.briar.android.login.StartupViewModel.State.STARTED;
import static org.briarproject.briar.android.login.StartupViewModel.State.STARTING;
import static org.briarproject.briar.android.login.StartupViewModel.State.TELEGRAM_LOGIN;
import static org.briarproject.briar.android.settings.SettingsFragment.SETTINGS_NAMESPACE;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;

@NotNullByDefault
public class StartupViewModel extends AndroidViewModel
		implements EventListener {

	enum State {SIGNED_OUT, TELEGRAM_LOGIN, SIGNED_IN, STARTING, MIGRATING, COMPACTING, STARTED}

	private static final Logger LOG =
			getLogger(StartupViewModel.class.getName());

	private final AccountManager accountManager;
	private final AndroidNotificationManager notificationManager;
	private final EventBus eventBus;
	private final FeatureFlags featureFlags;
	private final SettingsManager settingsManager;
	private final TelegramAuthSession telegramAuthSession;
	@IoExecutor
	private final Executor ioExecutor;

	private final MutableLiveEvent<DecryptionResult> passwordValidated =
			new MutableLiveEvent<>();
	private final MutableLiveEvent<Boolean> accountDeleted =
			new MutableLiveEvent<>();
	private final MutableLiveEvent<String> telegramLinkedIdentityStaged =
			new MutableLiveEvent<>();
	private final MutableLiveData<State> state = new MutableLiveData<>();
	private final MutableLiveData<TelegramAuthState> telegramAuthState =
			new MutableLiveData<>(TelegramAuthState.CLOSED);
	private String telegramLoginIdentifier = "";
	private String telegramLoginCode = "";
	private String telegramLoginPassword = "";
	private volatile String pendingTelegramLinkedIdentity = "";
	private volatile String lastTelegramLinkedIdentityStaged = "";

	@Inject
	StartupViewModel(Application app,
			AccountManager accountManager,
			LifecycleManager lifecycleManager,
			AndroidNotificationManager notificationManager,
			EventBus eventBus,
			@IoExecutor Executor ioExecutor,
			SettingsManager settingsManager,
			FeatureFlags featureFlags,
			TelegramAuthSession telegramAuthSession) {
		super(app);
		this.accountManager = accountManager;
		this.notificationManager = notificationManager;
		this.eventBus = eventBus;
		this.ioExecutor = ioExecutor;
		this.settingsManager = settingsManager;
		this.featureFlags = featureFlags;
		this.telegramAuthSession = telegramAuthSession;

		updateState(lifecycleManager.getLifecycleState());
		eventBus.addListener(this);
	}

	@Override
	protected void onCleared() {
		telegramAuthSession.close();
		eventBus.removeListener(this);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof LifecycleEvent) {
			LifecycleState s = ((LifecycleEvent) e).getLifecycleState();
			updateState(s);
		}
	}

	@UiThread
	private void updateState(LifecycleState s) {
		if (accountManager.hasDatabaseKey()) {
			if (s.isAfter(STARTING_SERVICES)) state.setValue(STARTED);
			else if (s == MIGRATING_DATABASE) state.setValue(MIGRATING);
			else if (s == COMPACTING_DATABASE) state.setValue(COMPACTING);
			else state.setValue(STARTING);
		} else {
			if (state.getValue() != TELEGRAM_LOGIN) state.setValue(SIGNED_OUT);
		}
	}

	boolean accountExists() {
		return accountManager.accountExists();
	}

	void clearSignInNotification() {
		notificationManager.blockSignInNotification();
		notificationManager.clearSignInNotification();
	}

	void validatePassword(String password) {
		ioExecutor.execute(() -> {
			try {
				accountManager.signIn(password);
				storePendingTelegramLinkedIdentity();
				passwordValidated.postEvent(SUCCESS);
				state.postValue(SIGNED_IN);
			} catch (DecryptionException e) {
				passwordValidated.postEvent(e.getDecryptionResult());
			}
		});
	}

	LiveEvent<DecryptionResult> getPasswordValidated() {
		return passwordValidated;
	}

	LiveEvent<Boolean> getAccountDeleted() {
		return accountDeleted;
	}

	LiveEvent<String> getTelegramLinkedIdentityStaged() {
		return telegramLinkedIdentityStaged;
	}

	String getLastTelegramLinkedIdentityStaged() {
		return lastTelegramLinkedIdentityStaged;
	}

	LiveData<State> getState() {
		return state;
	}

	void showTelegramLoginPlaceholder() {
		pendingTelegramLinkedIdentity = telegramLoginCode = telegramLoginPassword = "";
		telegramAuthSession.start();
		telegramAuthState.setValue(telegramAuthSession.getCurrentState());
		state.setValue(TELEGRAM_LOGIN);
	}

	String getTelegramLoginIdentifier() {
		return telegramLoginIdentifier;
	}

	void setTelegramLoginIdentifier(String identifier) {
		telegramLoginIdentifier = identifier;
	}

	String getTelegramLoginCode() {
		return telegramLoginCode;
	}

	void setTelegramLoginCode(String code) {
		telegramLoginCode = code;
	}

	String getTelegramLoginPassword() {
		return telegramLoginPassword;
	}

	void setTelegramLoginPassword(String password) {
		telegramLoginPassword = password;
	}

	LiveData<TelegramAuthState> getTelegramAuthState() {
		return telegramAuthState;
	}

	RecoverableErrorDetail getTelegramRecoverableErrorDetail() {
		return telegramAuthSession.getRecoverableErrorDetail();
	}

	void submitTelegramLoginIdentifier() {
		telegramLoginCode = telegramLoginPassword = "";
		telegramAuthSession.submitIdentifier(telegramLoginIdentifier.trim());
		telegramAuthState.setValue(telegramAuthSession.getCurrentState());
	}

	void submitTelegramLoginCode() {
		telegramAuthSession.submitCode(telegramLoginCode.trim());
		telegramAuthState.setValue(telegramAuthSession.getCurrentState());
		if (telegramAuthState.getValue() != TelegramAuthState.RECOVERABLE_ERROR ||
				getTelegramRecoverableErrorDetail() != RecoverableErrorDetail.INVALID_CODE) {
			telegramLoginCode = "";
		}
	}

	void submitTelegramLoginPassword() {
		telegramAuthSession.submitPassword(telegramLoginPassword);
		telegramAuthState.setValue(telegramAuthSession.getCurrentState());
		if (telegramAuthState.getValue() != TelegramAuthState.RECOVERABLE_ERROR ||
				getTelegramRecoverableErrorDetail() != RecoverableErrorDetail.INVALID_PASSWORD) {
			telegramLoginPassword = "";
		}
	}

	void completeTelegramLoginConfirmation() {
		pendingTelegramLinkedIdentity = telegramLoginIdentifier.trim();
		showPasswordFragment();
	}

	void showTelegramLoginIdentifierStep() {
		telegramLoginCode = telegramLoginPassword = "";
		telegramAuthSession.close();
		telegramAuthSession.start();
		telegramAuthState.setValue(telegramAuthSession.getCurrentState());
	}

	boolean isShowingTelegramLoginConfirmation() {
		TelegramAuthState authState = telegramAuthState.getValue();
		return authState == TelegramAuthState.CODE_ENTRY ||
				authState == TelegramAuthState.PASSWORD_ENTRY ||
				authState == TelegramAuthState.READY ||
				authState == TelegramAuthState.RECOVERABLE_ERROR &&
						(getTelegramRecoverableErrorDetail() == RecoverableErrorDetail.INVALID_CODE ||
								getTelegramRecoverableErrorDetail() == RecoverableErrorDetail.INVALID_PASSWORD);
	}

	void showPasswordFragment() {
		telegramLoginIdentifier = telegramLoginCode = telegramLoginPassword = "";
		telegramAuthSession.close();
		telegramAuthState.setValue(telegramAuthSession.getCurrentState());
		state.setValue(SIGNED_OUT);
	}

	private void storePendingTelegramLinkedIdentity() {
		if (pendingTelegramLinkedIdentity.isEmpty()) return;
		try {
			Settings settings = new Settings();
			settings.put("pref_key_telegram_linked_identity",
					pendingTelegramLinkedIdentity);
			settingsManager.mergeSettings(settings, SETTINGS_NAMESPACE);
			lastTelegramLinkedIdentityStaged = pendingTelegramLinkedIdentity;
			telegramLinkedIdentityStaged.postEvent(lastTelegramLinkedIdentityStaged);
			pendingTelegramLinkedIdentity = "";
		} catch (DbException e) {
			logException(LOG, WARNING, e);
		}
	}

	boolean shouldShowTelegramLogin() {
		return featureFlags.shouldEnableTelegramConnector();
	}

	@UiThread
	void deleteAccount() {
		accountManager.deleteAccount();
		accountDeleted.setEvent(true);
	}

}
