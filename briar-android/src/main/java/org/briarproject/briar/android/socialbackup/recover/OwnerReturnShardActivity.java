package org.briarproject.briar.android.socialbackup.recover;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.api.socialbackup.ReturnShardPayload;
import org.briarproject.briar.api.socialbackup.recovery.RestoreAccount;
import org.briarproject.briar.api.socialbackup.recovery.SecretOwnerTask;

import java.security.GeneralSecurityException;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_TASK_ON_HOME;
import static java.util.logging.Logger.getLogger;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class OwnerReturnShardActivity extends BaseActivity
		implements BaseFragment.BaseFragmentListener {

	private static final Logger LOG =
			getLogger(OwnerReturnShardActivity.class.getName());

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private OwnerReturnShardViewModel viewModel;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(OwnerReturnShardViewModel.class);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_fragment_container);
		if (state == null) {
			showInitialFragment(new OwnerRecoveryModeExplainerFragment());
		}
		viewModel.getShowQrCodeFragment().observeEvent(this, show -> {
			if (show) {
				LOG.info("Show QR code clicked");
				viewModel.startListening();
				showQrCodeFragment();
			}
		});
		viewModel.getStartClicked().observeEvent(this, start -> {
			if (start) {
				showNextFragment(new OwnerRecoveryModeMainFragment());
			}
		});
		viewModel.getSuccessDismissed().observeEvent(this, success -> {
			if (success) onSuccessDismissed();
		});
		viewModel.getErrorTryAgain().observeEvent(this, tryAgain -> {
			if (tryAgain) {
				viewModel.startListening();
				showQrCodeFragment();
			}
		});
		viewModel.getState()
				.observe(this, this::onReturnShardStateChanged);
	}

	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	protected void onPostResume() {
		super.onPostResume();
//		viewModel.setIsActivityResumed(true);
	}

	@Override
	protected void onPause() {
		super.onPause();
//		viewModel.setIsActivityResumed(false);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBackPressed() {
		// TODO should we cancel the return shard task here?
		super.onBackPressed();
	}

	private void showQrCodeFragment() {
		LOG.info("showQrCodeFragment called");
		FragmentManager fm = getSupportFragmentManager();
		if (fm.findFragmentByTag(OwnerReturnShardFragment.TAG) == null) {
			BaseFragment f = OwnerReturnShardFragment.newInstance();
			fm.beginTransaction()
					.replace(R.id.fragmentContainer, f, f.getUniqueTag())
					.addToBackStack(f.getUniqueTag())
					.commit();
		}
	}

	private void onSuccessDismissed() {
		finish();
		Intent i = new Intent(this, RestoreAccountActivity.class);
		i.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP |
				FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_TASK_ON_HOME);
		startActivity(i);
	}

	private void onReturnShardStateChanged(SecretOwnerTask.State state) {
		if (state instanceof SecretOwnerTask.State.Success) {
			ReturnShardPayload shardPayload =
					((SecretOwnerTask.State.Success) state).getRemotePayload();
			RestoreAccount.AddReturnShardPayloadResult result = viewModel.addToShardSet(shardPayload);
			if (result == RestoreAccount.AddReturnShardPayloadResult.MISMATCH) {
				// TODO improve this
				Toast.makeText(this,
						"WARNING: Mismatched backup piece!",
						Toast.LENGTH_LONG).show();
			}
			boolean added = result !=
					RestoreAccount.AddReturnShardPayloadResult.DUPLICATE;
			Toast.makeText(this,
					"Success - got backup piece" + (added ? "" : " duplicate"),
					Toast.LENGTH_SHORT).show();
			if (added && viewModel.canRecover()) {
				LOG.info("Secret key recovered");
				try {
					viewModel.recover();
				} catch (GeneralSecurityException e) {
					LOG.warning("Unable to decrypt backup" + e.toString());
					Toast.makeText(this,
							"Unable to decrypt backup",
							Toast.LENGTH_LONG).show();
					return;
				} catch (FormatException e) {
					LOG.warning("Unable to parse backup" + e.getMessage() +
							e.getStackTrace().toString());
					Toast.makeText(this,
							"Unable to parse backup",
							Toast.LENGTH_LONG).show();
					return;
				}
				showNextFragment(new OwnerReturnShardSuccessFragment());
				return;
			}
			onBackPressed();
		} else if (state instanceof SecretOwnerTask.State.Failure) {
			showNextFragment(new OwnerRecoveryModeErrorFragment());
		}
	}

	@Override
	@Deprecated
	public void runOnDbThread(Runnable runnable) {
		throw new RuntimeException("Don't use this deprecated method here.");
	}
}
