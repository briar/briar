package org.briarproject.briar.android.conversation;

import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.widget.Toast;

import org.briarproject.bramble.api.connection.ConnectionRegistry;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.ContactItem;

import java.util.Random;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.os.Build.VERSION.SDK_INT;
import static androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.util.UiUtils.getGoToSettingsListener;
import static org.briarproject.briar.android.util.UiUtils.isLocationEnabled;
import static org.briarproject.briar.android.util.UiUtils.showLocationDialog;

class BluetoothConnecter {

	private final Logger LOG = getLogger(BluetoothConnecter.class.getName());

	private enum Permission {
		UNKNOWN, GRANTED, SHOW_RATIONALE, PERMANENTLY_DENIED
	}

	private final Application app;
	private final PluginManager pluginManager;
	private final Executor ioExecutor;
	private final AndroidExecutor androidExecutor;
	private final ConnectionRegistry connectionRegistry;
	private final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();

	private volatile Plugin bluetoothPlugin;

	private Permission locationPermission = Permission.UNKNOWN;

	@Inject
	BluetoothConnecter(Application app,
			PluginManager pluginManager,
			@IoExecutor Executor ioExecutor,
			AndroidExecutor androidExecutor,
			ConnectionRegistry connectionRegistry) {
		this.app = app;
		this.pluginManager = pluginManager;
		this.ioExecutor = ioExecutor;
		this.androidExecutor = androidExecutor;
		this.bluetoothPlugin = pluginManager.getPlugin(BluetoothConstants.ID);
		this.connectionRegistry = connectionRegistry;
	}

	boolean isConnectedViaBluetooth(ContactId contactId) {
		return connectionRegistry.isConnected(contactId, BluetoothConstants.ID);
	}

	boolean isDiscovering() {
		// TODO bluetoothPlugin.isDiscovering()
		return false;
	}

	/**
	 * Call this when the using activity or fragment starts,
	 * because permissions might have changed while it was stopped.
	 */
	void reset() {
		locationPermission = Permission.UNKNOWN;
		// When this class is instantiated before we are logged in
		// (like when returning to a killed activity), bluetoothPlugin would be
		// null and we consider bluetooth not supported. So reset here.
		bluetoothPlugin = pluginManager.getPlugin(BluetoothConstants.ID);
	}

	@UiThread
	void onLocationPermissionResult(Activity activity,
			@Nullable Boolean result) {
		if (result != null && result) {
			locationPermission = Permission.GRANTED;
		} else if (shouldShowRequestPermissionRationale(activity,
				ACCESS_FINE_LOCATION)) {
			locationPermission = Permission.SHOW_RATIONALE;
		} else {
			locationPermission = Permission.PERMANENTLY_DENIED;
		}
	}

	boolean isBluetoothNotSupported() {
		return bt == null || bluetoothPlugin == null;
	}

	boolean areRequirementsFulfilled(Context ctx,
			ActivityResultLauncher<String> permissionRequest,
			Runnable onLocationDenied) {
		boolean permissionGranted =
				SDK_INT < 23 || locationPermission == Permission.GRANTED;
		boolean locationEnabled = isLocationEnabled(ctx);
		if (permissionGranted && locationEnabled) return true;

		if (locationPermission == Permission.PERMANENTLY_DENIED) {
			showDenialDialog(ctx, onLocationDenied);
		} else if (locationPermission == Permission.SHOW_RATIONALE) {
			showRationale(ctx, permissionRequest);
		} else if (!locationEnabled) {
			showLocationDialog(ctx);
		}
		return false;
	}

	private void showDenialDialog(Context ctx, Runnable onLocationDenied) {
		new AlertDialog.Builder(ctx, R.style.BriarDialogTheme)
				.setTitle(R.string.permission_location_title)
				.setMessage(R.string.permission_location_denied_body)
				.setPositiveButton(R.string.ok, getGoToSettingsListener(ctx))
				.setNegativeButton(R.string.cancel, (v, d) ->
						onLocationDenied.run())
				.show();
	}

	private void showRationale(Context ctx,
			ActivityResultLauncher<String> permissionRequest) {
		new AlertDialog.Builder(ctx, R.style.BriarDialogTheme)
				.setTitle(R.string.permission_location_title)
				.setMessage(R.string.permission_location_request_body)
				.setPositiveButton(R.string.ok, (dialog, which) ->
						permissionRequest.launch(ACCESS_FINE_LOCATION))
				.show();
	}

	@UiThread
	void onBluetoothDiscoverable(ContactItem contact) {
		connect(contact.getContact().getId());
	}

	private void connect(ContactId contactId) {
		// TODO
		//  * enable bluetooth connections setting, if not enabled
		//  * wait for plugin to become active
		ioExecutor.execute(() -> {
			Random r = new Random();
			try {
				showToast(R.string.toast_connect_via_bluetooth_start);
				// TODO do real work here
				Thread.sleep(r.nextInt(3000) + 3000);
				if (r.nextBoolean()) {
					showToast(R.string.toast_connect_via_bluetooth_success);
				} else {
					showToast(R.string.toast_connect_via_bluetooth_error);
				}
			} catch (InterruptedException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void showToast(@StringRes int res) {
		androidExecutor.runOnUiThread(() ->
				Toast.makeText(app, res, Toast.LENGTH_LONG).show()
		);
	}

}
