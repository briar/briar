package org.briarproject.briar.android.socialbackup.recover;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.api.socialbackup.recovery.SecretOwnerTask;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
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

//	private final ActivityResultLauncher<String[]> permissionLauncher =
//			registerForActivityResult(
//					new ActivityResultContracts.RequestMultiplePermissions(),
//					r ->
//							permissionManager.onRequestPermissionResult(r,
//									viewModel::showQrCodeFragmentIfAllowed));

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(OwnerReturnShardViewModel.class);
	}

	// TODO the following two methods should be injected from messageParser
//	private Shard parseShardMessage(BdfList body) throws FormatException {
//		// Message type, secret ID, shard
//		byte[] secretId = body.getRaw(1);
//		byte[] shard = body.getRaw(2);
//		return new Shard(secretId, shard);
//	}
//
//	private ReturnShardPayload parseReturnShardPayload(BdfList body)
//			throws FormatException {
//		checkSize(body, 2);
//		Shard shard = parseShardMessage(body.getList(0));
//		org.briarproject.briar.api.socialbackup.BackupPayload backupPayload =
//				new BackupPayload(body.getRaw(1));
//		return new ReturnShardPayload(shard, backupPayload);
//	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_fragment_container);
		if (state == null) {
			showInitialFragment(new OwnerRecoveryModeExplainerFragment());
		}
		viewModel.getShowQrCodeFragment().observeEvent(this, show -> {
			if (show) showQrCodeFragment();
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
		if (viewModel.getState()
				.getValue() instanceof SecretOwnerTask.State.Failure) {
			// re-create this activity when going back in failed state
			Intent i = new Intent(this, OwnerReturnShardActivity.class);
			i.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(i);
		} else {
			super.onBackPressed();
		}
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

	private void onReturnShardStateChanged(SecretOwnerTask.State state) {
		if (state instanceof SecretOwnerTask.State.Success) {
			Toast.makeText(this,
					"Success - got shard",
					Toast.LENGTH_SHORT).show();
			finish();
		} else if (state instanceof SecretOwnerTask.State.Failure) {
			// TODO error screen
			Toast.makeText(this,
					"Shard return failed!",
					Toast.LENGTH_SHORT).show();
			onBackPressed();
//			showNextFragment(new OwnerRecoveryModeExplainerFragment());
		}
	}

//	private void showErrorFragment() {
//		// TODO change this for an appropriate error message fragment
//		showNextFragment(new AddNearbyContactErrorFragment());
//	}

	@Override
	@Deprecated
	public void runOnDbThread(Runnable runnable) {
		throw new RuntimeException("Don't use this deprecated method here.");
	}
}
