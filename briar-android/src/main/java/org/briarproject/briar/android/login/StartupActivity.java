package org.briarproject.briar.android.login;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import org.briarproject.briar.R;
import org.briarproject.briar.android.BriarService;
import org.briarproject.briar.android.account.SetupActivity;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.briar.android.login.StartupViewModel.State;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.fragment.app.Fragment;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_TASK_ON_HOME;
import static android.widget.Toast.LENGTH_LONG;
import static org.briarproject.briar.android.login.StartupViewModel.State.SIGNED_IN;
import static org.briarproject.briar.android.login.StartupViewModel.State.SIGNED_OUT;
import static org.briarproject.briar.android.login.StartupViewModel.State.STARTED;
import static org.briarproject.briar.android.login.StartupViewModel.State.STARTING;
import static org.briarproject.briar.android.login.StartupViewModel.State.TELEGRAM_LOGIN;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class StartupActivity extends BaseActivity implements
		BaseFragmentListener {

	private static final String KEY_STAGED_TELEGRAM_LOGIN_IDENTITY =
			"stagedTelegramLoginIdentity";
	public static final String EXTRA_STAGED_TELEGRAM_LOGIN_IDENTITY =
			"briar.STAGED_TELEGRAM_LOGIN_IDENTITY";

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private StartupViewModel viewModel;
	private String stagedTelegramLoginIdentity = "";

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(StartupViewModel.class);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		// fade-in after splash screen instead of default animation
		overridePendingTransition(R.anim.fade_in, R.anim.fade_out);

		setContentView(R.layout.activity_fragment_container);
		if (state != null) {
			stagedTelegramLoginIdentity = state.getString(
					KEY_STAGED_TELEGRAM_LOGIN_IDENTITY, "");
		}

		if (!viewModel.accountExists()) {
			// TODO ideally we would not have to delete the account again
			// The account needs to deleted again to remove the database folder,
			// because if it exists, we assume the database also exists
			// and when clearing app data, the folder does not get deleted.
			viewModel.deleteAccount();
			onAccountDeleted();
			return;
		}
		viewModel.getAccountDeleted().observeEvent(this, deleted -> {
			if (deleted) onAccountDeleted();
		});
		viewModel.getTelegramLinkedIdentityStaged().observeEvent(this,
				identifier -> {
					stagedTelegramLoginIdentity = identifier;
					Toast.makeText(this,
							getString(
									R.string.telegram_connector_login_handoff_staged,
									identifier),
							LENGTH_LONG).show();
				});
		viewModel.getState().observe(this, this::onStateChanged);
	}

	@Override
	protected void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		state.putString(KEY_STAGED_TELEGRAM_LOGIN_IDENTITY,
				stagedTelegramLoginIdentity);
	}

	@Override
	public void onStart() {
		super.onStart();
		viewModel.clearSignInNotification();
	}

	@Override
	@SuppressLint("MissingSuperCall")
	public void onBackPressed() {
		if (viewModel.getState().getValue() == TELEGRAM_LOGIN) {
			if (viewModel.isShowingTelegramLoginConfirmation()) {
				viewModel.showTelegramLoginIdentifierStep();
				return;
			}
			viewModel.showPasswordFragment();
			return;
		}
		// Move task and activity to the background instead of showing another
		// password prompt.
		// onActivityResult() won't be called in BriarActivity
		moveTaskToBack(true);
	}

	private void onStateChanged(State state) {
		if (state == SIGNED_OUT) {
			// Configuration changes such as screen rotation
			// can cause this to get called again.
			showPasswordFragment();
		} else if (state == TELEGRAM_LOGIN) {
			showTelegramLoginPlaceholder();
		} else if (state == SIGNED_IN || state == STARTING) {
			startService(new Intent(this, BriarService.class));
			showNextFragment(new OpenDatabaseFragment());
		} else if (state == STARTED) {
			Intent result = new Intent();
			if (!stagedTelegramLoginIdentity.isEmpty()) {
				result.putExtra(EXTRA_STAGED_TELEGRAM_LOGIN_IDENTITY,
						stagedTelegramLoginIdentity);
			}
			setResult(RESULT_OK, result);
			supportFinishAfterTransition();
			overridePendingTransition(R.anim.screen_new_in,
					R.anim.screen_old_out);
		}
	}

	private void onAccountDeleted() {
		setResult(RESULT_CANCELED);
		finish();
		Intent i = new Intent(this, SetupActivity.class);
		i.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP |
				FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_TASK_ON_HOME);
		startActivity(i);
	}

	private void showPasswordFragment() {
		Fragment current = getSupportFragmentManager()
				.findFragmentById(R.id.fragmentContainer);
		if (current instanceof PasswordFragment) return;

		if (getSupportFragmentManager().findFragmentByTag(PasswordFragment.TAG)
				!= null &&
				getSupportFragmentManager().getBackStackEntryCount() > 0) {
			getSupportFragmentManager().popBackStack();
		} else {
			showInitialFragment(new PasswordFragment());
		}
	}

	private void showTelegramLoginPlaceholder() {
		showNextFragment(TelegramLoginPlaceholderFragment.newInstance());
	}

	@Override
	public void runOnDbThread(Runnable runnable) {
		// we don't need this and shouldn't be forced to implement it
		throw new UnsupportedOperationException();
	}

}
