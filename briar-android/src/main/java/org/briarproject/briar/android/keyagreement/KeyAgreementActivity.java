package org.briarproject.briar.android.keyagreement;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog.Builder;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.Toast;

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
import static android.os.Build.VERSION.SDK_INT;
import static android.widget.Toast.LENGTH_LONG;
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

	private static final Logger LOG =
			Logger.getLogger(KeyAgreementActivity.class.getName());

	@Inject
	EventBus eventBus;

	private boolean isResumed = false, wasAdapterEnabled = false;
	private boolean continueClicked, gotCameraPermission, gotLocationPermission;
	private BluetoothState bluetoothState = BluetoothState.UNKNOWN;
	private BroadcastReceiver bluetoothReceiver = null;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@SuppressWarnings("ConstantConditions")
	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_fragment_container_toolbar);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onPostResume() {
		super.onPostResume();
		isResumed = true;
		// Workaround for
		// https://code.google.com/p/android/issues/detail?id=190966
		if (canShowQrCodeFragment()) showQrCodeFragment();
	}

	private boolean canShowQrCodeFragment() {
		return isResumed && continueClicked
				&& (SDK_INT < 23 || gotCameraPermission)
				&& bluetoothState != BluetoothState.UNKNOWN
				&& bluetoothState != BluetoothState.WAITING;
	}

	@Override
	protected void onPause() {
		super.onPause();
		isResumed = false;
	}

	@Override
	public void showNextScreen() {
		continueClicked = true;
		if (checkPermissions()) {
			if (shouldRequestBluetoothDiscoverable()) {
				requestBluetoothDiscoverable();
			} else if (canShowQrCodeFragment()) {
				showQrCodeFragment();
			}
		}
	}

	private boolean shouldRequestBluetoothDiscoverable() {
		return bluetoothState == BluetoothState.UNKNOWN
				|| bluetoothState == BluetoothState.REFUSED;
	}

	private void requestBluetoothDiscoverable() {
		BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
		if (bt == null) {
			setBluetoothState(BluetoothState.NO_ADAPTER);
		} else {
			setBluetoothState(BluetoothState.WAITING);
			wasAdapterEnabled = bt.isEnabled();
			Intent i = new Intent(ACTION_REQUEST_DISCOVERABLE);
			startActivityForResult(i, REQUEST_BLUETOOTH_DISCOVERABLE);
		}
	}

	private void setBluetoothState(BluetoothState bluetoothState) {
		LOG.info("Setting Bluetooth state to " + bluetoothState);
		this.bluetoothState = bluetoothState;
		if (!wasAdapterEnabled && bluetoothState == BluetoothState.ENABLED) {
			eventBus.broadcast(new BluetoothEnabledEvent());
			wasAdapterEnabled = true;
		}
		if (canShowQrCodeFragment()) showQrCodeFragment();
	}

	@Override
	public void onActivityResult(int request, int result, Intent data) {
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
		}
	}

	private void showQrCodeFragment() {
		continueClicked = false;
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

	protected void showErrorFragment(@StringRes int errorResId) {
		String errorMsg = getString(errorResId);
		BaseFragment f = ContactExchangeErrorFragment.newInstance(errorMsg);
		showNextFragment(f);
	}

	private boolean checkPermissions() {
		gotCameraPermission = checkPermission(CAMERA);
		gotLocationPermission = checkPermission(ACCESS_COARSE_LOCATION);
		if (gotCameraPermission && gotLocationPermission) return true;
		// Should we show an explanation for one or both permissions?
		boolean cameraRationale = shouldShowRationale(CAMERA);
		boolean locationRationale = shouldShowRationale(ACCESS_COARSE_LOCATION);
		if (cameraRationale && locationRationale) {
			showRationale(R.string.permission_camera_location_title,
					R.string.permission_camera_location_request_body);
		} else if (cameraRationale) {
			showRationale(R.string.permission_camera_title,
					R.string.permission_camera_request_body);
		} else if (locationRationale) {
			showRationale(R.string.permission_location_title,
					R.string.permission_location_request_body);
		} else if (gotCameraPermission) {
			// Location permission has been permanently denied but we can
			// continue without it
			return true;
		} else {
			requestPermissions();
		}
		return false;
	}

	private boolean checkPermission(String permission) {
		return ContextCompat.checkSelfPermission(this, permission)
				== PERMISSION_GRANTED;
	}

	private boolean shouldShowRationale(String permission) {
		return ActivityCompat.shouldShowRequestPermissionRationale(this,
				permission);
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
			String permissions[], int[] grantResults) {
		if (requestCode == REQUEST_PERMISSION_CAMERA_LOCATION) {
			// If request is cancelled, the result arrays are empty
			gotCameraPermission = grantResults.length > 0
					&& grantResults[0] == PERMISSION_GRANTED;
			gotLocationPermission = grantResults.length > 1
					&& grantResults[1] == PERMISSION_GRANTED;
			if (gotCameraPermission) {
				showNextScreen();
			} else {
				if (shouldShowRationale(CAMERA)) {
					Toast.makeText(this,
							R.string.permission_camera_denied_toast,
							LENGTH_LONG).show();
					supportFinishAfterTransition();
				} else {
					// The user has permanently denied the request
					Builder builder =
							new Builder(this, R.style.BriarDialogTheme);
					builder.setTitle(R.string.permission_camera_title);
					builder.setMessage(R.string.permission_camera_denied_body);
					builder.setPositiveButton(R.string.ok,
							UiUtils.getGoToSettingsListener(this));
					builder.setNegativeButton(R.string.cancel,
							(dialog, which) -> supportFinishAfterTransition());
					builder.show();
				}
			}
		}
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
