package org.briarproject.briar.android.conversation;

import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.widget.Toast;

import org.briarproject.bramble.api.connection.ConnectionManager;
import org.briarproject.bramble.api.connection.ConnectionRegistry;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.plugin.event.ConnectionOpenedEvent;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.plugin.bluetooth.BluetoothPlugin;
import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.ContactItem;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.os.Build.VERSION.SDK_INT;
import static androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.ID;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_UUID;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;
import static org.briarproject.briar.android.util.UiUtils.getGoToSettingsListener;
import static org.briarproject.briar.android.util.UiUtils.isLocationEnabled;
import static org.briarproject.briar.android.util.UiUtils.showLocationDialog;

class BluetoothConnecter implements EventListener {

	private final Logger LOG = getLogger(BluetoothConnecter.class.getName());

	private final long BT_ACTIVE_TIMEOUT = SECONDS.toMillis(5);

	private enum Permission {
		UNKNOWN, GRANTED, SHOW_RATIONALE, PERMANENTLY_DENIED
	}

	private final Application app;
	private final PluginManager pluginManager;
	private final Executor ioExecutor;
	private final AndroidExecutor androidExecutor;
	private final ConnectionRegistry connectionRegistry;
	private final BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
	private final EventBus eventBus;
	private final TransportPropertyManager transportPropertyManager;
	private final ConnectionManager connectionManager;

	private volatile BluetoothPlugin bluetoothPlugin;

	private Permission locationPermission = Permission.UNKNOWN;
	private ContactId contactId = null;

	@Inject
	BluetoothConnecter(Application app,
			PluginManager pluginManager,
			@IoExecutor Executor ioExecutor,
			AndroidExecutor androidExecutor,
			ConnectionRegistry connectionRegistry,
			EventBus eventBus,
			TransportPropertyManager transportPropertyManager,
			ConnectionManager connectionManager) {
		this.app = app;
		this.pluginManager = pluginManager;
		this.ioExecutor = ioExecutor;
		this.androidExecutor = androidExecutor;
		this.bluetoothPlugin = (BluetoothPlugin) pluginManager.getPlugin(ID);
		this.connectionRegistry = connectionRegistry;
		this.eventBus = eventBus;
		this.transportPropertyManager = transportPropertyManager;
		this.connectionManager = connectionManager;
	}

	boolean isConnectedViaBluetooth(ContactId contactId) {
		return connectionRegistry.isConnected(contactId, ID);
	}

	boolean isDiscovering() {
		return bluetoothPlugin.isDiscovering();
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
		bluetoothPlugin = (BluetoothPlugin) pluginManager.getPlugin(ID);
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
		contactId = contact.getContact().getId();
		connect();
	}

	@Override
	public void eventOccurred(@NonNull Event e) {
		if (e instanceof ConnectionOpenedEvent) {
			ConnectionOpenedEvent c = (ConnectionOpenedEvent) e;
			if (c.getContactId().equals(contactId) && c.isIncoming() &&
					c.getTransportId() == ID) {
				if (bluetoothPlugin != null) {
					bluetoothPlugin.stopDiscoverAndConnect();
				}
				LOG.info("Contact connected to us");
				showToast(R.string.toast_connect_via_bluetooth_success);
			}
		}
	}

	private void connect() {
		pluginManager.setPluginEnabled(ID, true);

		ioExecutor.execute(() -> {
			if (!waitForBluetoothActive()) {
				showToast(R.string.bt_plugin_status_inactive);
				LOG.warning("Bluetooth plugin didn't become active");
				return;
			}
			showToast(R.string.toast_connect_via_bluetooth_start);
			eventBus.addListener(this);
			try {
				String uuid = null;
				try {
					uuid = transportPropertyManager
							.getRemoteProperties(contactId, ID).get(PROP_UUID);
				} catch (DbException e) {
					logException(LOG, WARNING, e);
				}
				if (isNullOrEmpty(uuid)) {
					LOG.warning("PROP_UUID missing for contact");
					return;
				}
				DuplexTransportConnection conn = bluetoothPlugin
						.discoverAndConnectForSetup(uuid);
				if (conn == null) {
					waitAfterConnectionFailed();
				} else {
					LOG.info("Could connect, handling connection");
					connectionManager
							.manageOutgoingConnection(contactId, ID, conn);
					showToast(R.string.toast_connect_via_bluetooth_success);
				}
			} finally {
				eventBus.removeListener(this);
			}
		});
	}

	@IoExecutor
	private boolean waitForBluetoothActive() {
		long left = BT_ACTIVE_TIMEOUT;
		final long sleep = 250;
		try {
			while (left > 0) {
				if (bluetoothPlugin.getState() == ACTIVE) {
					return true;
				}
				Thread.sleep(sleep);
				left -= sleep;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return (bluetoothPlugin.getState() == ACTIVE);
	}

	/**
	 * Wait for an incoming connection before showing an error Toast.
	 */
	@IoExecutor
	private void waitAfterConnectionFailed() {
		long left = BT_ACTIVE_TIMEOUT;
		final long sleep = 250;
		try {
			while (left > 0) {
				if (isConnectedViaBluetooth(contactId)) {
					LOG.info("Failed to connect, but contact connected");
					// no Toast needed here, as it gets shown when
					// ConnectionOpenedEvent is received
					return;
				}
				Thread.sleep(sleep);
				left -= sleep;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		LOG.warning("Failed to connect");
		showToast(R.string.toast_connect_via_bluetooth_error);
	}

	private void showToast(@StringRes int res) {
		androidExecutor.runOnUiThread(() ->
				Toast.makeText(app, res, Toast.LENGTH_LONG).show()
		);
	}

}
