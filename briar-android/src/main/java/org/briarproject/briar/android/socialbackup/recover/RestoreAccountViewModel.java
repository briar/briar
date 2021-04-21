package org.briarproject.briar.android.socialbackup.recover;

import android.app.Application;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.crypto.PasswordStrengthEstimator;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.account.DozeHelper;
import org.briarproject.briar.android.viewmodel.LiveEvent;
import org.briarproject.briar.android.viewmodel.MutableLiveEvent;
import org.briarproject.briar.api.socialbackup.recovery.RestoreAccount;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.logging.Logger.getLogger;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class RestoreAccountViewModel extends AndroidViewModel {
	enum State {SET_PASSWORD, DOZE, CREATED, FAILED}

	private static final Logger LOG =
			getLogger(RestoreAccountViewModel.class.getName());

	@Nullable
	private String password;
	private final MutableLiveEvent<State>
			state = new MutableLiveEvent<>();
	private final MutableLiveData<Boolean> isCreatingAccount =
			new MutableLiveData<>(false);

	private final AccountManager accountManager;
	private final Executor ioExecutor;
	private final PasswordStrengthEstimator strengthEstimator;
	private final DozeHelper dozeHelper;
	private final RestoreAccount restoreAccount;

	@Inject
	RestoreAccountViewModel(Application app,
			AccountManager accountManager,
			RestoreAccount restoreAccount,
			@IoExecutor Executor ioExecutor,
			PasswordStrengthEstimator strengthEstimator,
			DozeHelper dozeHelper) {
		super(app);
		this.accountManager = accountManager;
		this.ioExecutor = ioExecutor;
		this.strengthEstimator = strengthEstimator;
		this.dozeHelper = dozeHelper;
		this.restoreAccount = restoreAccount;

		ioExecutor.execute(() -> {
			if (accountManager.accountExists()) {
				throw new AssertionError();
			} else {
				state.postEvent(State.SET_PASSWORD);
			}
		});
	}

	LiveEvent<State> getState() {
		return state;
	}

	LiveData<Boolean> getIsCreatingAccount() {
		return isCreatingAccount;
	}

//	void setAuthorName(String authorName) {
//		this.authorName = authorName;
//		state.setEvent(SET_PASSWORD);
//	}

	void setPassword(String password) {
		this.password = password;
		if (needToShowDozeFragment()) {
			state.setEvent(State.DOZE);
		} else {
			createAccount();
		}
	}

	float estimatePasswordStrength(String password) {
		return strengthEstimator.estimateStrength(password);
	}

	boolean needToShowDozeFragment() {
		return dozeHelper.needToShowDozeFragment(getApplication());
	}

	void dozeExceptionConfirmed() {
		createAccount();
	}

	private void createAccount() {
//		if (authorName == null) throw new IllegalStateException();
		if (password == null) throw new IllegalStateException();
		isCreatingAccount.setValue(true);
		Identity identity = restoreAccount.getSocialBackup().getIdentity();
		ioExecutor.execute(() -> {
			if (accountManager.restoreAccount(identity, password)) {
				LOG.info("Restore account");
				state.postEvent(State.CREATED);
			} else {
				LOG.warning("Failed to create account");
				state.postEvent(State.FAILED);
			}
		});
	}
}
