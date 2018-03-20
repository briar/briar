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

import org.briarproject.bramble.api.contact.ContactExchangeListener;
import org.briarproject.bramble.api.contact.ContactExchangeTask;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.keyagreement.KeyAgreementResult;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementFinishedEvent;
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
import static java.util.logging.Level.WARNING;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_ENABLE_BLUETOOTH;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_PERMISSION_CAMERA;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class KeyAgreementActivity extends BriarActivity implements
		BaseFragmentListener, IntroScreenSeenListener, EventListener,
		ContactExchangeListener {

	private enum BluetoothState {
		UNKNOWN, NO_ADAPTER, WAITING, REFUSED, ENABLED
	}

	private static final Logger LOG =
			Logger.getLogger(KeyAgreementActivity.class.getName());

	@Inject
	EventBus eventBus;

	// Fields that are accessed from background threads must be volatile
	@Inject
	volatile ContactExchangeTask contactExchangeTask;
	@Inject
	volatile IdentityManager identityManager;

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

		getSupportActionBar().setTitle(R.string.add_contact_title);
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
	public void onStart() {
		super.onStart();
		eventBus.addListener(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		eventBus.removeListener(this);
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

	boolean canShowQrCodeFragment() {
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
		// FIXME #824
		FragmentManager fm = getSupportFragmentManager();
		if (fm.findFragmentByTag(ShowQrCodeFragment.TAG) == null) {
			BaseFragment f = ShowQrCodeFragment.newInstance();
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

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof KeyAgreementFinishedEvent) {
			KeyAgreementFinishedEvent event = (KeyAgreementFinishedEvent) e;
			keyAgreementFinished(event.getResult());
		}
	}

	private void keyAgreementFinished(KeyAgreementResult result) {
		runOnUiThreadUnlessDestroyed(() -> startContactExchange(result));
	}

	private void startContactExchange(KeyAgreementResult result) {
		runOnDbThread(() -> {
			LocalAuthor localAuthor;
			// Load the local pseudonym
			try {
				localAuthor = identityManager.getLocalAuthor();
			} catch (DbException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				contactExchangeFailed();
				return;
			}

			// Exchange contact details
			contactExchangeTask.startExchange(KeyAgreementActivity.this,
					localAuthor, result.getMasterKey(),
					result.getConnection(), result.getTransportId(),
					result.wasAlice());
		});
	}

	@Override
	public void contactExchangeSucceeded(Author remoteAuthor) {
		runOnUiThreadUnlessDestroyed(() -> {
			String contactName = remoteAuthor.getName();
			String format = getString(string.contact_added_toast);
			String text = String.format(format, contactName);
			Toast.makeText(KeyAgreementActivity.this, text, LENGTH_LONG).show();
			supportFinishAfterTransition();
		});
	}

	@Override
	public void duplicateContact(Author remoteAuthor) {
		runOnUiThreadUnlessDestroyed(() -> {
			String contactName = remoteAuthor.getName();
			String format = getString(string.contact_already_exists);
			String text = String.format(format, contactName);
			Toast.makeText(KeyAgreementActivity.this, text, LENGTH_LONG).show();
			finish();
		});
	}

	@Override
	public void contactExchangeFailed() {
		runOnUiThreadUnlessDestroyed(() -> {
			Toast.makeText(KeyAgreementActivity.this,
					string.contact_exchange_failed, LENGTH_LONG).show();
			finish();
		});
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
