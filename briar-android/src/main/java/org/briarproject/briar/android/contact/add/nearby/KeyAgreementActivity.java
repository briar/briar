package org.briarproject.briar.android.contact.add.nearby;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.MenuItem;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.BluetoothDecision;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog.Builder;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.CAMERA;
import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.ACTION_SCAN_MODE_CHANGED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_BLUETOOTH_DISCOVERABLE;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_PERMISSION_CAMERA_LOCATION;
import static org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.BluetoothDecision.ACCEPTED;
import static org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.BluetoothDecision.REFUSED;
import static org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.BluetoothDecision.UNKNOWN;
import static org.briarproject.briar.android.util.UiUtils.getGoToSettingsListener;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class KeyAgreementActivity extends BriarActivity
		implements BaseFragmentListener {

	private enum Permission {
		UNKNOWN, GRANTED, SHOW_RATIONALE, PERMANENTLY_DENIED
	}

	private static final Logger LOG =
			getLogger(KeyAgreementActivity.class.getName());

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	protected ContactExchangeViewModel viewModel;

	/**
	 * Set to true in onPostResume() and false in onPause(). This prevents the
	 * QR code fragment from being shown if onRequestPermissionsResult() is
	 * called while the activity is paused, which could cause a crash due to
	 * https://issuetracker.google.com/issues/37067655.
	 */
	private boolean isResumed = false;

	private Permission cameraPermission = Permission.UNKNOWN;
	private Permission locationPermission = Permission.UNKNOWN;
	private BroadcastReceiver bluetoothReceiver = null;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(ContactExchangeViewModel.class);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_fragment_container_toolbar);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
		if (state == null) {
			showInitialFragment(IntroFragment.newInstance());
		}
		IntentFilter filter = new IntentFilter(ACTION_SCAN_MODE_CHANGED);
		bluetoothReceiver = new BluetoothStateReceiver();
		registerReceiver(bluetoothReceiver, filter);
		viewModel.getWasContinueClicked().observe(this, clicked -> {
			if (clicked && checkPermissions()) showQrCodeFragmentIfAllowed();
		});
		viewModel.getTransportStateChanged().observeEvent(this,
				t -> showQrCodeFragmentIfAllowed());
		viewModel.getShowQrCodeFragment().observeEvent(this, show -> {
			if (show) showQrCodeFragment();
		});
	}

	@Override
	public void onStart() {
		super.onStart();
		// Permissions may have been granted manually while we were stopped
		cameraPermission = Permission.UNKNOWN;
		locationPermission = Permission.UNKNOWN;
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
	protected void onStop() {
		super.onStop();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (bluetoothReceiver != null) unregisterReceiver(bluetoothReceiver);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@SuppressWarnings("StatementWithEmptyBody")
	private void showQrCodeFragmentIfAllowed() {
		boolean continueClicked = // never set to null
				requireNonNull(viewModel.getWasContinueClicked().getValue());
		if (isResumed && continueClicked && areEssentialPermissionsGranted()) {
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

	private boolean areEssentialPermissionsGranted() {
		return cameraPermission == Permission.GRANTED &&
				(SDK_INT < 23 || locationPermission == Permission.GRANTED ||
						!viewModel.isBluetoothSupported());
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

	private void showQrCodeFragment() {
		// FIXME #824
		FragmentManager fm = getSupportFragmentManager();
		if (fm.findFragmentByTag(KeyAgreementFragment.TAG) == null) {
			BaseFragment f = KeyAgreementFragment.newInstance();
			fm.beginTransaction()
					.replace(R.id.fragmentContainer, f, f.getUniqueTag())
					.addToBackStack(f.getUniqueTag())
					.commit();
		}
	}

	private boolean checkPermissions() {
		if (areEssentialPermissionsGranted()) return true;
		// If an essential permission has been permanently denied, ask the
		// user to change the setting
		if (cameraPermission == Permission.PERMANENTLY_DENIED) {
			showDenialDialog(R.string.permission_camera_title,
					R.string.permission_camera_denied_body);
			return false;
		}
		if (viewModel.isBluetoothSupported() &&
				locationPermission == Permission.PERMANENTLY_DENIED) {
			showDenialDialog(R.string.permission_location_title,
					R.string.permission_location_denied_body);
			return false;
		}
		// Should we show the rationale for one or both permissions?
		if (cameraPermission == Permission.SHOW_RATIONALE &&
				locationPermission == Permission.SHOW_RATIONALE) {
			showRationale(R.string.permission_camera_location_title,
					R.string.permission_camera_location_request_body);
		} else if (cameraPermission == Permission.SHOW_RATIONALE) {
			showRationale(R.string.permission_camera_title,
					R.string.permission_camera_request_body);
		} else if (locationPermission == Permission.SHOW_RATIONALE) {
			showRationale(R.string.permission_location_title,
					R.string.permission_location_request_body);
		} else {
			requestPermissions();
		}
		return false;
	}

	private void showDenialDialog(@StringRes int title, @StringRes int body) {
		Builder builder = new Builder(this, R.style.BriarDialogTheme);
		builder.setTitle(title);
		builder.setMessage(body);
		builder.setPositiveButton(R.string.ok, getGoToSettingsListener(this));
		builder.setNegativeButton(R.string.cancel,
				(dialog, which) -> supportFinishAfterTransition());
		builder.show();
	}

	private void showRationale(@StringRes int title, @StringRes int body) {
		Builder builder = new Builder(this, R.style.BriarDialogTheme);
		builder.setTitle(title);
		builder.setMessage(body);
		builder.setNeutralButton(R.string.continue_button,
				(dialog, which) -> requestPermissions());
		builder.show();
	}

	private void requestPermissions() {
		String[] permissions;
		if (viewModel.isBluetoothSupported()) {
			permissions = new String[] {CAMERA, ACCESS_FINE_LOCATION};
		} else {
			permissions = new String[] {CAMERA};
		}
		ActivityCompat.requestPermissions(this, permissions,
				REQUEST_PERMISSION_CAMERA_LOCATION);
	}

	@Override
	@UiThread
	public void onRequestPermissionsResult(int requestCode,
			String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions,
				grantResults);
		if (requestCode != REQUEST_PERMISSION_CAMERA_LOCATION)
			throw new AssertionError();
		if (gotPermission(CAMERA, permissions, grantResults)) {
			cameraPermission = Permission.GRANTED;
		} else if (shouldShowRationale(CAMERA)) {
			cameraPermission = Permission.SHOW_RATIONALE;
		} else {
			cameraPermission = Permission.PERMANENTLY_DENIED;
		}
		if (viewModel.isBluetoothSupported()) {
			if (gotPermission(ACCESS_FINE_LOCATION, permissions,
					grantResults)) {
				locationPermission = Permission.GRANTED;
			} else if (shouldShowRationale(ACCESS_FINE_LOCATION)) {
				locationPermission = Permission.SHOW_RATIONALE;
			} else {
				locationPermission = Permission.PERMANENTLY_DENIED;
			}
		}
		// If a permission dialog has been shown, showing the QR code fragment
		// on this call path would cause a crash due to
		// https://code.google.com/p/android/issues/detail?id=190966.
		// In that case the isResumed flag prevents the fragment from being
		// shown here, and showQrCodeFragmentIfAllowed() will be called again
		// from onPostResume().
		if (checkPermissions()) showQrCodeFragmentIfAllowed();
	}

	private boolean gotPermission(String permission, String[] permissions,
			int[] grantResults) {
		for (int i = 0; i < permissions.length; i++) {
			if (permission.equals(permissions[i]))
				return grantResults[i] == PERMISSION_GRANTED;
		}
		return false;
	}

	private boolean shouldShowRationale(String permission) {
		return ActivityCompat.shouldShowRequestPermissionRationale(this,
				permission);
	}

	private class BluetoothStateReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			LOG.info("Bluetooth scan mode changed");
			showQrCodeFragmentIfAllowed();
		}
	}
}
