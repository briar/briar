package org.briarproject.briar.android.keyagreement;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
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
import org.briarproject.briar.R.string;
import org.briarproject.briar.R.style;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.briar.android.keyagreement.IntroFragment.IntroScreenSeenListener;
import org.briarproject.briar.android.util.UiUtils;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.Manifest.permission.CAMERA;
import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE;
import static android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED;
import static android.bluetooth.BluetoothAdapter.EXTRA_STATE;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.widget.Toast.LENGTH_LONG;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_ENABLE_BLUETOOTH;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_PERMISSION_CAMERA;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class KeyAgreementActivity extends BriarActivity implements
		BaseFragmentListener, IntroScreenSeenListener,
		KeyAgreementFragment.KeyAgreementEventListener {

	private enum BluetoothState {
		UNKNOWN, NO_ADAPTER, WAITING, REFUSED, ENABLED
	}

	private static final Logger LOG =
			Logger.getLogger(KeyAgreementActivity.class.getName());

	@Inject
	EventBus eventBus;

	private boolean isResumed = false, enableWasRequested = false;
	private boolean continueClicked, gotCameraPermission;
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
		IntentFilter filter = new IntentFilter(ACTION_STATE_CHANGED);
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
			if (shouldRequestEnableBluetooth()) requestEnableBluetooth();
			else if (canShowQrCodeFragment()) showQrCodeFragment();
		}
	}

	private boolean shouldRequestEnableBluetooth() {
		return bluetoothState == BluetoothState.UNKNOWN
				|| bluetoothState == BluetoothState.REFUSED;
	}

	private void requestEnableBluetooth() {
		BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
		if (bt == null) {
			setBluetoothState(BluetoothState.NO_ADAPTER);
		} else if (bt.isEnabled()) {
			setBluetoothState(BluetoothState.ENABLED);
		} else {
			enableWasRequested = true;
			setBluetoothState(BluetoothState.WAITING);
			Intent i = new Intent(ACTION_REQUEST_ENABLE);
			startActivityForResult(i, REQUEST_ENABLE_BLUETOOTH);
		}
	}

	private void setBluetoothState(BluetoothState bluetoothState) {
		LOG.info("Setting Bluetooth state to " + bluetoothState);
		this.bluetoothState = bluetoothState;
		if (enableWasRequested && bluetoothState == BluetoothState.ENABLED) {
			eventBus.broadcast(new BluetoothEnabledEvent());
			enableWasRequested = false;
		}
		if (canShowQrCodeFragment()) showQrCodeFragment();
	}

	@Override
	public void onActivityResult(int request, int result, Intent data) {
		// If the request was granted we'll catch the state change event
		if (request == REQUEST_ENABLE_BLUETOOTH && result == RESULT_CANCELED)
			setBluetoothState(BluetoothState.REFUSED);
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

	private boolean checkPermissions() {
		if (ContextCompat.checkSelfPermission(this, CAMERA) !=
				PERMISSION_GRANTED) {
			// Should we show an explanation?
			if (ActivityCompat.shouldShowRequestPermissionRationale(this,
					CAMERA)) {
				OnClickListener continueListener =
						(dialog, which) -> requestPermission();
				Builder builder = new Builder(this, style.BriarDialogTheme);
				builder.setTitle(string.permission_camera_title);
				builder.setMessage(string.permission_camera_request_body);
				builder.setNeutralButton(string.continue_button,
						continueListener);
				builder.show();
			} else {
				requestPermission();
			}
			gotCameraPermission = false;
			return false;
		} else {
			gotCameraPermission = true;
			return true;
		}
	}

	private void requestPermission() {
		ActivityCompat.requestPermissions(this, new String[] {CAMERA},
				REQUEST_PERMISSION_CAMERA);
	}

	@Override
	@UiThread
	public void onRequestPermissionsResult(int requestCode,
			String permissions[], int[] grantResults) {
		if (requestCode == REQUEST_PERMISSION_CAMERA) {
			// If request is cancelled, the result arrays are empty.
			if (grantResults.length > 0 &&
					grantResults[0] == PERMISSION_GRANTED) {
				gotCameraPermission = true;
				showNextScreen();
			} else {
				if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
						CAMERA)) {
					// The user has permanently denied the request
					OnClickListener cancelListener =
							(dialog, which) -> supportFinishAfterTransition();
					Builder builder = new Builder(this, style.BriarDialogTheme);
					builder.setTitle(string.permission_camera_title);
					builder.setMessage(string.permission_camera_denied_body);
					builder.setPositiveButton(string.ok,
							UiUtils.getGoToSettingsListener(this));
					builder.setNegativeButton(string.cancel, cancelListener);
					builder.show();
				} else {
					Toast.makeText(this, string.permission_camera_denied_toast,
							LENGTH_LONG).show();
					supportFinishAfterTransition();
				}
			}
		}
	}

	private class BluetoothStateReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			int state = intent.getIntExtra(EXTRA_STATE, 0);
			if (state == STATE_ON) setBluetoothState(BluetoothState.ENABLED);
			else setBluetoothState(BluetoothState.UNKNOWN);
		}
	}
}
