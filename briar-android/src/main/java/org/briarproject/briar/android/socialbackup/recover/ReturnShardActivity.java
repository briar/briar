package org.briarproject.briar.android.socialbackup.recover;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.contact.add.nearby.AddNearbyContactErrorFragment;
import org.briarproject.briar.android.contact.add.nearby.AddNearbyContactFragment;
import org.briarproject.briar.android.contact.add.nearby.AddNearbyContactPermissionManager;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.util.RequestBluetoothDiscoverable;
import org.briarproject.briar.api.socialbackup.BackupPayload;
import org.briarproject.briar.api.socialbackup.MessageParser;
import org.briarproject.briar.api.socialbackup.ReturnShardPayload;
import org.briarproject.briar.api.socialbackup.Shard;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.ValidationUtils.checkSize;
import static org.briarproject.briar.android.socialbackup.CustodianHelpRecoverActivity.RETURN_SHARD_PAYLOAD;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ReturnShardActivity extends BaseActivity
		implements BaseFragment.BaseFragmentListener {

	private static final Logger LOG =
			getLogger(ReturnShardActivity.class.getName());

	@Inject
	ViewModelProvider.Factory viewModelFactory;

//	@Inject
//	MessageParser messageParser;

	@Inject
	public ClientHelper clientHelper;

	private ReturnShardViewModel viewModel;
	private AddNearbyContactPermissionManager permissionManager;

	private final ActivityResultLauncher<String[]> permissionLauncher =
			registerForActivityResult(
					new ActivityResultContracts.RequestMultiplePermissions(),
					r ->
							permissionManager.onRequestPermissionResult(r,
									viewModel::showQrCodeFragmentIfAllowed));
	private final ActivityResultLauncher<Integer> bluetoothLauncher =
			registerForActivityResult(new RequestBluetoothDiscoverable(),
					this::onBluetoothDiscoverableResult);

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(ReturnShardViewModel.class);
		permissionManager = new AddNearbyContactPermissionManager(this,
				permissionLauncher::launch, viewModel.isBluetoothSupported());
	}

	// TODO the following two methods should be injected from messageParser
	private Shard parseShardMessage(BdfList body) throws FormatException {
		// Message type, secret ID, shard
		byte[] secretId = body.getRaw(1);
		byte[] shard = body.getRaw(2);
		return new Shard(secretId, shard);
	}

	private ReturnShardPayload parseReturnShardPayload(BdfList body)
			throws FormatException {
		checkSize(body, 2);
		Shard shard = parseShardMessage(body.getList(0));
		org.briarproject.briar.api.socialbackup.BackupPayload backupPayload =
				new BackupPayload(body.getRaw(1));
		return new ReturnShardPayload(shard, backupPayload);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		byte[] returnShardPayloadBytes =
				getIntent().getByteArrayExtra(RETURN_SHARD_PAYLOAD);
		if (returnShardPayloadBytes != null) {
			try {
				ReturnShardPayload returnShardPayload = parseReturnShardPayload(
						clientHelper.toList(returnShardPayloadBytes));
				viewModel.setSending(true);
				viewModel.setReturnShardPayload(returnShardPayload);
			} catch (FormatException e) {
				Toast.makeText(this,
						"Error reading social backup",
						Toast.LENGTH_SHORT).show();
				finish();
			}
		}
		setContentView(R.layout.activity_fragment_container);
		if (state == null) {
			showInitialFragment(getExplainerFragment());
		}
		viewModel.getCheckPermissions().observeEvent(this, check ->
				permissionManager.checkPermissions());
		viewModel.getRequestBluetoothDiscoverable().observeEvent(this, r ->
				requestBluetoothDiscoverable()); // never false
		viewModel.getShowQrCodeFragment().observeEvent(this, show -> {
			if (show) showQrCodeFragment();
		});
		viewModel.getState()
				.observe(this, this::onReturnShardStateChanged);
	}

	public BaseFragment getExplainerFragment() {
		return new OwnerRecoveryModeExplainerFragment();
	}

	@Override
	public void onStart() {
		super.onStart();
		// Permissions may have been granted manually while we were stopped
		permissionManager.resetPermissions();
	}

	@Override
	protected void onPostResume() {
		super.onPostResume();
		viewModel.setIsActivityResumed(true);
	}

	@Override
	protected void onPause() {
		super.onPause();
		viewModel.setIsActivityResumed(false);
	}

	private void onBluetoothDiscoverableResult(boolean discoverable) {
		if (discoverable) {
			LOG.info("Bluetooth discoverability was accepted");
			viewModel.setBluetoothDecision(
					ReturnShardViewModel.BluetoothDecision.ACCEPTED);
		} else {
			LOG.info("Bluetooth discoverability was refused");
			viewModel.setBluetoothDecision(
					ReturnShardViewModel.BluetoothDecision.REFUSED);
		}
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
		if (viewModel.getState()
				.getValue() instanceof ReturnShardState.Failed) {
			// re-create this activity when going back in failed state
			Intent i = new Intent(this, ReturnShardActivity.class);
			i.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(i);
		} else {
			super.onBackPressed();
		}
	}

	private void requestBluetoothDiscoverable() {
		if (!viewModel.isBluetoothSupported()) {
			viewModel.setBluetoothDecision(
					ReturnShardViewModel.BluetoothDecision.NO_ADAPTER);
		} else {
			Intent i = new Intent(ACTION_REQUEST_DISCOVERABLE);
			if (i.resolveActivity(getPackageManager()) != null) {
				LOG.info("Asking for Bluetooth discoverability");
				viewModel.setBluetoothDecision(
						ReturnShardViewModel.BluetoothDecision.WAITING);
				bluetoothLauncher.launch(120); // 2min discoverable
			} else {
				viewModel.setBluetoothDecision(
						ReturnShardViewModel.BluetoothDecision.NO_ADAPTER);
			}
		}
	}

	private void showQrCodeFragment() {
		// FIXME #824
		FragmentManager fm = getSupportFragmentManager();
		if (fm.findFragmentByTag(AddNearbyContactFragment.TAG) == null) {
			BaseFragment f = AddNearbyContactFragment.newInstance();
			fm.beginTransaction()
					.replace(R.id.fragmentContainer, f, f.getUniqueTag())
					.addToBackStack(f.getUniqueTag())
					.commit();
		}
	}

	private void onReturnShardStateChanged(ReturnShardState state) {
		if (state instanceof ReturnShardState.SocialBackupExchangeFinished) {
			ReturnShardState.SocialBackupExchangeResult result =
					((ReturnShardState.SocialBackupExchangeFinished) state).result;
			onSocialBackupExchangeResult(result);
		} else if (state instanceof ReturnShardState.Failed) {
			Boolean qrCodeTooOld =
					((ReturnShardState.Failed) state).qrCodeTooOld;
			onAddingContactFailed(qrCodeTooOld);
		}
	}

	private void onSocialBackupExchangeResult(
			ReturnShardState.SocialBackupExchangeResult result) {
		if (result instanceof ReturnShardState.SocialBackupExchangeResult.Success) {
//			String text = getString(R.string.contact_added_toast, contactName);
			Toast.makeText(this, "Shard return successful", LENGTH_LONG).show();
			supportFinishAfterTransition();
		} else if (result instanceof ReturnShardState.SocialBackupExchangeResult.Error) {
			showErrorFragment();
		} else throw new AssertionError();
	}

	private void onAddingContactFailed(@Nullable Boolean qrCodeTooOld) {
		if (qrCodeTooOld == null) {
			showErrorFragment();
		} else {
			String msg;
			if (qrCodeTooOld) {
				msg = getString(R.string.qr_code_too_old,
						getString(R.string.app_name));
			} else {
				msg = getString(R.string.qr_code_too_new,
						getString(R.string.app_name));
			}
			showNextFragment(AddNearbyContactErrorFragment.newInstance(msg));
		}
	}

	private void showErrorFragment() {
		showNextFragment(new AddNearbyContactErrorFragment());
	}

	@Override
	@Deprecated
	public void runOnDbThread(Runnable runnable) {
		throw new RuntimeException("Don't use this deprecated method here.");
	}
}
