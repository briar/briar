package org.briarproject.briar.android.conversation;

import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.widget.Toast;

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
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.util.UiUtils.getGoToSettingsListener;

class BluetoothConnecter {

	private final Logger LOG = getLogger(BluetoothConnecter.class.getName());

	private final Application app;
	private final Executor ioExecutor;
	private final AndroidExecutor androidExecutor;

	private final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
	private final Plugin bluetoothPlugin;

	@Inject
	BluetoothConnecter(Application app,
			PluginManager pluginManager,
			@IoExecutor Executor ioExecutor,
			AndroidExecutor androidExecutor) {
		this.app = app;
		this.ioExecutor = ioExecutor;
		this.androidExecutor = androidExecutor;
		this.bluetoothPlugin = pluginManager.getPlugin(BluetoothConstants.ID);
	}

	static void showDialog(Context ctx,
			ActivityResultLauncher<String> permissionRequest) {
		new AlertDialog.Builder(ctx, R.style.BriarDialogTheme)
				.setTitle(R.string.dialog_title_connect_via_bluetooth)
				.setMessage(R.string.dialog_message_connect_via_bluetooth)
				.setPositiveButton(R.string.start, (dialog, which) ->
						permissionRequest.launch(ACCESS_FINE_LOCATION))
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	@UiThread
	void onLocationPermissionResult(Activity activity, boolean result,
			ActivityResultLauncher<Integer> bluetoothDiscoverableRequest) {
		if (result) {
			if (isBluetoothSupported()) {
				bluetoothDiscoverableRequest.launch(120);
			} else {
				showToast(R.string.toast_connect_via_bluetooth_error);
			}
		} else if (shouldShowRequestPermissionRationale(activity,
				ACCESS_FINE_LOCATION)) {
			showToast(R.string.permission_location_denied_body);
		} else {
			showRationale(activity);
		}
	}

	private boolean isBluetoothSupported() {
		return bt != null && bluetoothPlugin != null;
	}

	private void showRationale(Context ctx) {
		new AlertDialog.Builder(ctx, R.style.BriarDialogTheme)
				.setTitle(R.string.permission_location_title)
				.setMessage(R.string.permission_location_request_body)
				.setPositiveButton(R.string.ok, getGoToSettingsListener(ctx))
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	@UiThread
	void onBluetoothDiscoverable(boolean result, ContactItem contact) {
		if (result) {
			connect(contact);
		} else {
			showToast(R.string.toast_connect_via_bluetooth_not_discoverable);
		}
	}

	private void connect(ContactItem contact) {
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
