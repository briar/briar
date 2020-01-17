package org.briarproject.briar.android.keyagreement;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.MenuItem;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.event.BluetoothEnabledEvent;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.briar.android.keyagreement.IntroFragment.IntroScreenSeenListener;
import org.briarproject.briar.android.keyagreement.KeyAgreementFragment.KeyAgreementEventListener;
import org.briarproject.briar.android.util.UiUtils;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog.Builder;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.CAMERA;
import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.ACTION_SCAN_MODE_CHANGED;
import static android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED;
import static android.bluetooth.BluetoothAdapter.EXTRA_SCAN_MODE;
import static android.bluetooth.BluetoothAdapter.EXTRA_STATE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_BLUETOOTH_DISCOVERABLE;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_PERMISSION_CAMERA_LOCATION;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class KeyAgreementActivity extends BriarActivity implements
		BaseFragmentListener, IntroScreenSeenListener,
		KeyAgreementEventListener {

	private enum BluetoothState {
		UNKNOWN, NO_ADAPTER, WAITING, REFUSED, ENABLED, DISCOVERABLE
	}

	private enum Permission {
		UNKNOWN, GRANTED, SHOW_RATIONALE, PERMANENTLY_DENIED
	}

	private static final Logger LOG =
			getLogger(KeyAgreementActivity.class.getName());

	@Inject
	EventBus eventBus;

	/**
	 * Set to true in onPostResume() and false in onPause(). This prevents the
	 * QR code fragment from being shown if onRequestPermissionsResult() is
	 * called while the activity is paused, which could cause a crash due to
	 * https://issuetracker.google.com/issues/37067655.
	 */
	private boolean isResumed = false;

	/**
	 * Set to true when the continue button is clicked, and false when the QR
	 * code fragment is shown. This prevents the QR code fragment from being
	 * shown automatically before the continue button has been clicked.
	 */
	private boolean continueClicked = false;

	/**
	 * Records whether the Bluetooth adapter was already enabled before we
	 * asked for Bluetooth discoverability, so we know whether to broadcast a
	 * {@link BluetoothEnabledEvent}.
	 */
	private boolean wasAdapterEnabled = false;

	private Permission cameraPermission = Permission.UNKNOWN;
	private Permission locationPermission = Permission.UNKNOWN;
	private BluetoothState bluetoothState = BluetoothState.UNKNOWN;
	private BroadcastReceiver bluetoothReceiver = null;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
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
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_STATE_CHANGED);
		filter.addAction(ACTION_SCAN_MODE_CHANGED);
		bluetoothReceiver = new BluetoothStateReceiver();
		registerReceiver(bluetoothReceiver, filter);
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

	private void showQrCodeFragmentIfAllowed() {
		if (isResumed && continueClicked && areEssentialPermissionsGranted()) {
			if (bluetoothState == BluetoothState.UNKNOWN ||
					bluetoothState == BluetoothState.ENABLED) {
				requestBluetoothDiscoverable();
			} else if (bluetoothState != BluetoothState.WAITING) {
				showQrCodeFragment();
			}
		}
	}

	private boolean areEssentialPermissionsGranted() {
		// If the camera permission has been granted, and the location
		// permission has been granted or permanently denied, we can continue
		return cameraPermission == Permission.GRANTED &&
				(locationPermission == Permission.GRANTED ||
						locationPermission == Permission.PERMANENTLY_DENIED);
	}

	@Override
	protected void onPause() {
		super.onPause();
		isResumed = false;
	}

	@Override
	public void showNextScreen() {
		continueClicked = true;
		if (checkPermissions()) showQrCodeFragmentIfAllowed();
	}

	private void requestBluetoothDiscoverable() {
		BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
		if (bt == null) {
			setBluetoothState(BluetoothState.NO_ADAPTER);
		} else {
			Intent i = new Intent(ACTION_REQUEST_DISCOVERABLE);
			if (i.resolveActivity(getPackageManager()) != null) {
				setBluetoothState(BluetoothState.WAITING);
				wasAdapterEnabled = bt.isEnabled();
				startActivityForResult(i, REQUEST_BLUETOOTH_DISCOVERABLE);
			} else {
				setBluetoothState(BluetoothState.NO_ADAPTER);
			}
		}
	}

	private void setBluetoothState(BluetoothState bluetoothState) {
		LOG.info("Setting Bluetooth state to " + bluetoothState);
		this.bluetoothState = bluetoothState;
		if (!wasAdapterEnabled && bluetoothState == BluetoothState.ENABLED) {
			eventBus.broadcast(new BluetoothEnabledEvent());
			wasAdapterEnabled = true;
		}
		showQrCodeFragmentIfAllowed();
	}

	@Override
	public void onActivityResult(int request, int result,
			@Nullable Intent data) {
		if (request == REQUEST_BLUETOOTH_DISCOVERABLE) {
			if (result == RESULT_CANCELED) {
				setBluetoothState(BluetoothState.REFUSED);
			} else {
				// If Bluetooth is already discoverable, show the QR code -
				// otherwise wait for the state or scan mode to change
				BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
				if (bt == null) throw new AssertionError();
				if (bt.getScanMode() == SCAN_MODE_CONNECTABLE_DISCOVERABLE)
					setBluetoothState(BluetoothState.DISCOVERABLE);
			}
		} else super.onActivityResult(request, result, data);
	}

	private void showQrCodeFragment() {
		// If we return to the intro fragment, the continue button needs to be
		// clicked again before showing the QR code fragment
		continueClicked = false;
		// If we return to the intro fragment, ask for Bluetooth
		// discoverability again before showing the QR code fragment
		bluetoothState = BluetoothState.UNKNOWN;
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
		// If the camera permission has been permanently denied, ask the
		// user to change the setting
		if (cameraPermission == Permission.PERMANENTLY_DENIED) {
			Builder builder = new Builder(this, R.style.BriarDialogTheme);
			builder.setTitle(R.string.permission_camera_title);
			builder.setMessage(R.string.permission_camera_denied_body);
			builder.setPositiveButton(R.string.ok,
					UiUtils.getGoToSettingsListener(this));
			builder.setNegativeButton(R.string.cancel,
					(dialog, which) -> supportFinishAfterTransition());
			builder.show();
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

	private void showRationale(@StringRes int title, @StringRes int body) {
		Builder builder = new Builder(this, R.style.BriarDialogTheme);
		builder.setTitle(title);
		builder.setMessage(body);
		builder.setNeutralButton(R.string.continue_button,
				(dialog, which) -> requestPermissions());
		builder.show();
	}

	private void requestPermissions() {
		ActivityCompat.requestPermissions(this,
				new String[] {CAMERA, ACCESS_COARSE_LOCATION},
				REQUEST_PERMISSION_CAMERA_LOCATION);
	}

	@Override
	@UiThread
	public void onRequestPermissionsResult(int requestCode,
			String[] permissions, int[] grantResults) {
		if (requestCode != REQUEST_PERMISSION_CAMERA_LOCATION)
			throw new AssertionError();
		if (gotPermission(CAMERA, permissions, grantResults)) {
			cameraPermission = Permission.GRANTED;
		} else if (shouldShowRationale(CAMERA)) {
			cameraPermission = Permission.SHOW_RATIONALE;
		} else {
			cameraPermission = Permission.PERMANENTLY_DENIED;
		}
		if (gotPermission(ACCESS_COARSE_LOCATION, permissions, grantResults)) {
			locationPermission = Permission.GRANTED;
		} else if (shouldShowRationale(ACCESS_COARSE_LOCATION)) {
			locationPermission = Permission.SHOW_RATIONALE;
		} else {
			locationPermission = Permission.PERMANENTLY_DENIED;
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
			String action = intent.getAction();
			if (ACTION_STATE_CHANGED.equals(action)) {
				int state = intent.getIntExtra(EXTRA_STATE, 0);
				if (state == STATE_ON)
					setBluetoothState(BluetoothState.ENABLED);
				else setBluetoothState(BluetoothState.UNKNOWN);
			} else if (ACTION_SCAN_MODE_CHANGED.equals(action)) {
				int scanMode = intent.getIntExtra(EXTRA_SCAN_MODE, 0);
				if (scanMode == SCAN_MODE_CONNECTABLE_DISCOVERABLE)
					setBluetoothState(BluetoothState.DISCOVERABLE);
				else if (scanMode == SCAN_MODE_CONNECTABLE)
					setBluetoothState(BluetoothState.ENABLED);
				else setBluetoothState(BluetoothState.UNKNOWN);
			}
		}
	}
}
