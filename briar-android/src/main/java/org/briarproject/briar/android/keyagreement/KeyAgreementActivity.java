package org.briarproject.briar.android.keyagreement;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.MenuItem;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.LanTcpConstants;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.Plugin.State;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.event.BluetoothEnabledEvent;
import org.briarproject.bramble.api.plugin.event.TransportStateEvent;
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
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.DISABLED;
import static org.briarproject.bramble.api.plugin.Plugin.State.INACTIVE;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_BLUETOOTH_DISCOVERABLE;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_PERMISSION_CAMERA_LOCATION;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class KeyAgreementActivity extends BriarActivity implements
		BaseFragmentListener, IntroScreenSeenListener,
		KeyAgreementEventListener, EventListener {

	private enum BluetoothDecision {
		/**
		 * We haven't asked the user about Bluetooth discoverability.
		 */
		UNKNOWN,

		/**
		 * The device doesn't have a Bluetooth adapter.
		 */
		NO_ADAPTER,

		/**
		 * We're waiting for the user to accept or refuse discoverability.
		 */
		WAITING,

		/**
		 * The user has accepted discoverability.
		 */
		ACCEPTED,

		/**
		 * The user has refused discoverability.
		 */
		REFUSED
	}

	private enum Permission {
		UNKNOWN, GRANTED, SHOW_RATIONALE, PERMANENTLY_DENIED
	}

	private static final Logger LOG =
			getLogger(KeyAgreementActivity.class.getName());

	@Inject
	EventBus eventBus;

	@Inject
	PluginManager pluginManager;

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

	/**
	 * Records whether we've enabled the wifi plugin so we don't enable it more
	 * than once.
	 */
	private boolean hasEnabledWifi = false;

	/**
	 * Records whether we've enabled the Bluetooth plugin so we don't enable it
	 * more than once.
	 */
	private boolean hasEnabledBluetooth = false;

	private Permission cameraPermission = Permission.UNKNOWN;
	private Permission locationPermission = Permission.UNKNOWN;
	private BluetoothDecision bluetoothDecision = BluetoothDecision.UNKNOWN;
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
		IntentFilter filter = new IntentFilter(ACTION_SCAN_MODE_CHANGED);
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
		eventBus.addListener(this);
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
			if (isWifiReady() && isBluetoothReady()) {
				LOG.info("Wifi and Bluetooth are ready");
				showQrCodeFragment();
			} else {
				if (shouldEnableWifi()) {
					LOG.info("Enabling wifi plugin");
					hasEnabledWifi = true;
					pluginManager.setPluginEnabled(LanTcpConstants.ID, true);
				}
				if (bluetoothDecision == BluetoothDecision.UNKNOWN) {
					requestBluetoothDiscoverable();
				} else if (shouldEnableBluetooth()) {
					LOG.info("Enabling Bluetooth plugin");
					hasEnabledBluetooth = true;
					pluginManager.setPluginEnabled(BluetoothConstants.ID, true);
				}
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

	private boolean isWifiReady() {
		Plugin p = pluginManager.getPlugin(LanTcpConstants.ID);
		if (p == null) return true; // Continue without wifi
		State state = p.getState();
		// Wait for plugin to become enabled
		return state == ACTIVE || state == INACTIVE;
	}

	private boolean isBluetoothReady() {
		if (bluetoothDecision == BluetoothDecision.UNKNOWN ||
				bluetoothDecision == BluetoothDecision.WAITING) {
			// Wait for decision
			return false;
		}
		if (bluetoothDecision == BluetoothDecision.NO_ADAPTER
				|| bluetoothDecision == BluetoothDecision.REFUSED) {
			// Continue without Bluetooth
			return true;
		}
		BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
		if (bt == null) return true; // Continue without Bluetooth
		if (bt.getScanMode() != SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			// Wait for adapter to become discoverable
			return false;
		}
		Plugin p = pluginManager.getPlugin(BluetoothConstants.ID);
		if (p == null) return true; // Continue without Bluetooth
		// Wait for plugin to become active
		return p.getState() == ACTIVE;
	}

	private boolean shouldEnableWifi() {
		if (hasEnabledWifi) return false;
		Plugin p = pluginManager.getPlugin(LanTcpConstants.ID);
		return p != null && p.getState() == DISABLED;
	}

	private void requestBluetoothDiscoverable() {
		BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
		if (bt == null) {
			bluetoothDecision = BluetoothDecision.NO_ADAPTER;
			showQrCodeFragmentIfAllowed();
		} else {
			Intent i = new Intent(ACTION_REQUEST_DISCOVERABLE);
			if (i.resolveActivity(getPackageManager()) != null) {
				LOG.info("Asking for Bluetooth discoverability");
				bluetoothDecision = BluetoothDecision.WAITING;
				wasAdapterEnabled = bt.isEnabled();
				startActivityForResult(i, REQUEST_BLUETOOTH_DISCOVERABLE);
			} else {
				bluetoothDecision = BluetoothDecision.NO_ADAPTER;
				showQrCodeFragmentIfAllowed();
			}
		}
	}

	private boolean shouldEnableBluetooth() {
		if (bluetoothDecision != BluetoothDecision.ACCEPTED) return false;
		if (hasEnabledBluetooth) return false;
		Plugin p = pluginManager.getPlugin(BluetoothConstants.ID);
		return p != null && p.getState() == DISABLED;
	}

	@Override
	protected void onPause() {
		super.onPause();
		isResumed = false;
	}

	@Override
	protected void onStop() {
		super.onStop();
		eventBus.removeListener(this);
	}

	@Override
	public void showNextScreen() {
		continueClicked = true;
		if (checkPermissions()) showQrCodeFragmentIfAllowed();
	}

	@Override
	public void onActivityResult(int request, int result,
			@Nullable Intent data) {
		if (request == REQUEST_BLUETOOTH_DISCOVERABLE) {
			if (result == RESULT_CANCELED) {
				LOG.info("Bluetooth discoverability was refused");
				bluetoothDecision = BluetoothDecision.REFUSED;
			} else {
				LOG.info("Bluetooth discoverability was accepted");
				bluetoothDecision = BluetoothDecision.ACCEPTED;
				if (!wasAdapterEnabled) {
					LOG.info("Bluetooth adapter was enabled by us");
					eventBus.broadcast(new BluetoothEnabledEvent());
					wasAdapterEnabled = true;
				}
			}
			showQrCodeFragmentIfAllowed();
		} else super.onActivityResult(request, result, data);
	}

	private void showQrCodeFragment() {
		// If we return to the intro fragment, the continue button needs to be
		// clicked again before showing the QR code fragment
		continueClicked = false;
		// If we return to the intro fragment, ask for Bluetooth
		// discoverability again before showing the QR code fragment
		bluetoothDecision = BluetoothDecision.UNKNOWN;
		// If we return to the intro fragment, we may need to enable wifi and
		// Bluetooth again
		hasEnabledWifi = false;
		hasEnabledBluetooth = false;

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

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof TransportStateEvent) {
			TransportStateEvent t = (TransportStateEvent) e;
			if (t.getTransportId().equals(BluetoothConstants.ID)) {
				if (LOG.isLoggable(INFO)) {
					LOG.info("Bluetooth state changed to " + t.getState());
				}
				showQrCodeFragmentIfAllowed();
			} else if (t.getTransportId().equals(LanTcpConstants.ID)) {
				if (LOG.isLoggable(INFO)) {
					LOG.info("Wifi state changed to " + t.getState());
				}
				showQrCodeFragmentIfAllowed();
			}
		}
	}

	private class BluetoothStateReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			LOG.info("Bluetooth scan mode changed");
			showQrCodeFragmentIfAllowed();
		}
	}
}
