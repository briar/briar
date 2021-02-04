package org.briarproject.briar.android.contact.add.nearby;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.NullSafety;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.ContactExchangeFinished;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.ContactExchangeResult;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.Failed;
import org.briarproject.briar.android.contact.add.nearby.AddNearbyContactViewModel.BluetoothDecision;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.ACTION_SCAN_MODE_CHANGED;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_BLUETOOTH_DISCOVERABLE;
import static org.briarproject.briar.android.contact.add.nearby.AddNearbyContactViewModel.BluetoothDecision.ACCEPTED;
import static org.briarproject.briar.android.contact.add.nearby.AddNearbyContactViewModel.BluetoothDecision.REFUSED;
import static org.briarproject.briar.android.contact.add.nearby.AddNearbyContactViewModel.BluetoothDecision.UNKNOWN;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class AddNearbyContactActivity extends BriarActivity
		implements BaseFragmentListener {

	private static final Logger LOG =
			getLogger(AddNearbyContactActivity.class.getName());

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private AddNearbyContactViewModel viewModel;
	private AddNearbyContactPermissionManager permissionManager;

	/**
	 * Set to true in onPostResume() and false in onPause(). This prevents the
	 * QR code fragment from being shown if onRequestPermissionsResult() is
	 * called while the activity is paused, which could cause a crash due to
	 * https://issuetracker.google.com/issues/37067655.
	 */
	private boolean isResumed = false;
	private BroadcastReceiver bluetoothReceiver = null;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(AddNearbyContactViewModel.class);
		permissionManager = new AddNearbyContactPermissionManager(this,
				viewModel.isBluetoothSupported());
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_fragment_container_toolbar);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		NullSafety.requireNonNull(getSupportActionBar())
				.setDisplayHomeAsUpEnabled(true);
		if (state == null) {
			showInitialFragment(AddNearbyContactIntroFragment.newInstance());
		}
		IntentFilter filter = new IntentFilter(ACTION_SCAN_MODE_CHANGED);
		bluetoothReceiver = new BluetoothStateReceiver();
		registerReceiver(bluetoothReceiver, filter);
		viewModel.getWasContinueClicked().observe(this, clicked -> {
			if (clicked && permissionManager.checkPermissions()) {
				showQrCodeFragmentIfAllowed();
			}
		});
		viewModel.getTransportStateChanged().observeEvent(this,
				t -> showQrCodeFragmentIfAllowed());
		viewModel.getShowQrCodeFragment().observeEvent(this, show -> {
			if (show) showQrCodeFragment();
		});
		requireNonNull(getSupportActionBar())
				.setTitle(R.string.add_contact_title);
		viewModel.getState()
				.observe(this, this::onAddContactStateChanged);
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
		isResumed = true;
		// Workaround for
		// https://code.google.com/p/android/issues/detail?id=190966
		showQrCodeFragmentIfAllowed();
	}

	@Override
	protected void onPause() {
		super.onPause();
		isResumed = false;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (bluetoothReceiver != null) unregisterReceiver(bluetoothReceiver);
	}

	@Override
	public void onActivityResult(int request, int result,
			@Nullable Intent data) {
		if (request == REQUEST_BLUETOOTH_DISCOVERABLE) {
			if (result == RESULT_CANCELED) {
				LOG.info("Bluetooth discoverability was refused");
				viewModel.bluetoothDecision = REFUSED;
			} else {
				LOG.info("Bluetooth discoverability was accepted");
				viewModel.bluetoothDecision = ACCEPTED;
			}
			showQrCodeFragmentIfAllowed();
		} else super.onActivityResult(request, result, data);
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
	public void onRequestPermissionsResult(int requestCode,
			String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions,
				grantResults);
		permissionManager.onRequestPermissionsResult(requestCode, permissions,
				grantResults, this::showQrCodeFragmentIfAllowed);
	}

	@Override
	public void onBackPressed() {
		if (viewModel.getState().getValue() instanceof Failed) {
			// finish this activity when going back in failed state
			supportFinishAfterTransition();
		} else {
			super.onBackPressed();
		}
	}

	private void requestBluetoothDiscoverable() {
		if (!viewModel.isBluetoothSupported()) {
			viewModel.bluetoothDecision = BluetoothDecision.NO_ADAPTER;
			showQrCodeFragmentIfAllowed();
		} else {
			Intent i = new Intent(ACTION_REQUEST_DISCOVERABLE);
			if (i.resolveActivity(getPackageManager()) != null) {
				LOG.info("Asking for Bluetooth discoverability");
				viewModel.bluetoothDecision = BluetoothDecision.WAITING;
				startActivityForResult(i, REQUEST_BLUETOOTH_DISCOVERABLE);
			} else {
				viewModel.bluetoothDecision = BluetoothDecision.NO_ADAPTER;
				showQrCodeFragmentIfAllowed();
			}
		}
	}

	@SuppressWarnings("StatementWithEmptyBody")
	private void showQrCodeFragmentIfAllowed() {
		boolean continueClicked = // never set to null
				NullSafety.requireNonNull(
						viewModel.getWasContinueClicked().getValue());
		boolean permissionsGranted =
				permissionManager.areEssentialPermissionsGranted();
		if (isResumed && continueClicked && permissionsGranted) {
			if (viewModel.isWifiReady() && viewModel.isBluetoothReady()) {
				LOG.info("Wifi and Bluetooth are ready");
				viewModel.startAddingContact();
			} else {
				viewModel.enableWifiIfWeShould();
				if (viewModel.bluetoothDecision == UNKNOWN) {
					requestBluetoothDiscoverable();
				} else if (viewModel.bluetoothDecision == REFUSED) {
					// Ask again when the user clicks "continue"
				} else {
					viewModel.enableBluetoothIfWeShould();
				}
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

	private void onAddContactStateChanged(AddContactState state) {
		if (state instanceof ContactExchangeFinished) {
			ContactExchangeResult result =
					((ContactExchangeFinished) state).result;
			onContactExchangeResult(result);
		} else if (state instanceof Failed) {
			// Remove navigation icon, so user can't go back when failed
			// ErrorFragment will finish or relaunch this activity
			Toolbar toolbar = findViewById(R.id.toolbar);
			toolbar.setNavigationIcon(null);

			Boolean qrCodeTooOld = ((Failed) state).qrCodeTooOld;
			onAddingContactFailed(qrCodeTooOld);
		}
	}

	private void onContactExchangeResult(ContactExchangeResult result) {
		if (result instanceof ContactExchangeResult.Success) {
			Author remoteAuthor =
					((ContactExchangeResult.Success) result).remoteAuthor;
			String contactName = remoteAuthor.getName();
			String text = getString(R.string.contact_added_toast, contactName);
			Toast.makeText(this, text, LENGTH_LONG).show();
			supportFinishAfterTransition();
		} else if (result instanceof ContactExchangeResult.Error) {
			Author duplicateAuthor =
					((ContactExchangeResult.Error) result).duplicateAuthor;
			if (duplicateAuthor == null) {
				showErrorFragment();
			} else {
				String contactName = duplicateAuthor.getName();
				String text =
						getString(R.string.contact_already_exists, contactName);
				Toast.makeText(this, text, LENGTH_LONG).show();
				supportFinishAfterTransition();
			}
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

	private class BluetoothStateReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			LOG.info("Bluetooth scan mode changed");
			showQrCodeFragmentIfAllowed();
		}
	}
}
