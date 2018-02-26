package org.briarproject.bramble.plugin.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.util.AndroidUtils;

import java.io.Closeable;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static android.bluetooth.BluetoothAdapter.ACTION_SCAN_MODE_CHANGED;
import static android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED;
import static android.bluetooth.BluetoothAdapter.EXTRA_SCAN_MODE;
import static android.bluetooth.BluetoothAdapter.EXTRA_STATE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_NONE;
import static android.bluetooth.BluetoothAdapter.STATE_OFF;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED;
import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothDevice.EXTRA_BOND_STATE;
import static android.bluetooth.BluetoothDevice.EXTRA_DEVICE;
import static android.bluetooth.BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.PrivacyUtils.scrubMacAddress;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class AndroidBluetoothPlugin extends BluetoothPlugin<BluetoothServerSocket> {

	private static final Logger LOG =
			Logger.getLogger(AndroidBluetoothPlugin.class.getName());

	private final AndroidExecutor androidExecutor;
	private final Context appContext;

	private volatile boolean wasEnabledByUs = false;
	private volatile BluetoothStateReceiver receiver = null;

	// Non-null if the plugin started successfully
	private volatile BluetoothAdapter adapter = null;

	AndroidBluetoothPlugin(BluetoothConnectionManager connectionManager,
			Executor ioExecutor, AndroidExecutor androidExecutor,
			Context appContext, SecureRandom secureRandom, Backoff backoff,
			DuplexPluginCallback callback, int maxLatency) {
		super(connectionManager, ioExecutor, secureRandom, backoff, callback,
				maxLatency);
		this.androidExecutor = androidExecutor;
		this.appContext = appContext;
	}

	@Override
	public void start() throws PluginException {
		super.start();
		// Listen for changes to the Bluetooth state
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_STATE_CHANGED);
		filter.addAction(ACTION_SCAN_MODE_CHANGED);
		filter.addAction(ACTION_BOND_STATE_CHANGED);
		receiver = new BluetoothStateReceiver();
		appContext.registerReceiver(receiver, filter);
	}

	@Override
	public void stop() {
		super.stop();
		if (receiver != null) appContext.unregisterReceiver(receiver);
	}

	@Override
	void initialiseAdapter() throws IOException {
		// BluetoothAdapter.getDefaultAdapter() must be called on a thread
		// with a message queue, so submit it to the AndroidExecutor
		try {
			adapter = androidExecutor.runOnBackgroundThread(
					BluetoothAdapter::getDefaultAdapter).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new IOException(e);
		}
		if (adapter == null)
			throw new IOException("Bluetooth is not supported");
	}

	@Override
	boolean isAdapterEnabled() {
		return adapter != null && adapter.isEnabled();
	}

	@Override
	void enableAdapter() {
		if (adapter != null && !adapter.isEnabled()) {
			if (adapter.enable()) {
				LOG.info("Enabling Bluetooth");
				wasEnabledByUs = true;
			} else {
				LOG.info("Could not enable Bluetooth");
			}
		}
	}

	@Override
	void disableAdapterIfEnabledByUs() {
		if (isAdapterEnabled() && wasEnabledByUs) {
			if (adapter.disable()) LOG.info("Disabling Bluetooth");
			else LOG.info("Could not disable Bluetooth");
			wasEnabledByUs = false;
		}
	}

	@Override
	void setEnabledByUs() {
		wasEnabledByUs = true;
	}

	@Override
	@Nullable
	String getBluetoothAddress() {
		String address = AndroidUtils.getBluetoothAddress(appContext, adapter);
		return address.isEmpty() ? null : address;
	}

	@Override
	BluetoothServerSocket openServerSocket(String uuid) throws IOException {
		return adapter.listenUsingInsecureRfcommWithServiceRecord(
				"RFCOMM", UUID.fromString(uuid));
	}

	@Override
	void tryToClose(@Nullable BluetoothServerSocket ss) {
		try {
			if (ss != null) ss.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	@Override
	DuplexTransportConnection acceptConnection(BluetoothServerSocket ss)
			throws IOException {
		return wrapSocket(ss.accept());
	}

	private DuplexTransportConnection wrapSocket(BluetoothSocket s) {
		return new AndroidBluetoothTransportConnection(this,
				connectionManager, s);
	}

	@Override
	boolean isValidAddress(String address) {
		return BluetoothAdapter.checkBluetoothAddress(address);
	}

	@Override
	DuplexTransportConnection connectTo(String address, String uuid)
			throws IOException {
		if (LOG.isLoggable(INFO)) {
			boolean found = false;
			List<String> addresses = new ArrayList<>();
			for (BluetoothDevice d : adapter.getBondedDevices()) {
				addresses.add(scrubMacAddress(d.getAddress()));
				if (d.getAddress().equals(address)) found = true;
			}
			LOG.info("Bonded devices: " + addresses);
			if (found) LOG.info("Connecting to bonded device");
			else LOG.info("Connecting to unbonded device");
		}
		BluetoothDevice d = adapter.getRemoteDevice(address);
		UUID u = UUID.fromString(uuid);
		BluetoothSocket s = null;
		try {
			s = d.createInsecureRfcommSocketToServiceRecord(u);
			s.connect();
			return wrapSocket(s);
		} catch (IOException e) {
			tryToClose(s);
			throw e;
		}
	}

	private void tryToClose(@Nullable Closeable c) {
		try {
			if (c != null) c.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private class BluetoothStateReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context ctx, Intent intent) {
			String action = intent.getAction();
			if (ACTION_STATE_CHANGED.equals(action)) {
				int state = intent.getIntExtra(EXTRA_STATE, 0);
				if (state == STATE_ON) onAdapterEnabled();
				else if (state == STATE_OFF) onAdapterDisabled();
			} else if (ACTION_SCAN_MODE_CHANGED.equals(action)) {
				int scanMode = intent.getIntExtra(EXTRA_SCAN_MODE, 0);
				if (scanMode == SCAN_MODE_NONE) {
					LOG.info("Scan mode: None");
				} else if (scanMode == SCAN_MODE_CONNECTABLE) {
					LOG.info("Scan mode: Connectable");
				} else if (scanMode == SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
					LOG.info("Scan mode: Discoverable");
				}
			} else if (ACTION_BOND_STATE_CHANGED.equals(action)) {
				BluetoothDevice d = intent.getParcelableExtra(EXTRA_DEVICE);
				if (LOG.isLoggable(INFO)) {
					LOG.info("Bond state changed for "
							+ scrubMacAddress(d.getAddress()));
				}
				int oldState = intent.getIntExtra(EXTRA_PREVIOUS_BOND_STATE, 0);
				if (oldState == BOND_NONE) {
					LOG.info("Old state: none");
				} else if (oldState == BOND_BONDING) {
					LOG.info("Old state: bonding");
				} else if (oldState == BOND_BONDED) {
					LOG.info("Old state: bonded");
				}
				int state = intent.getIntExtra(EXTRA_BOND_STATE, 0);
				if (state == BOND_NONE) {
					LOG.info("New state: none");
				} else if (state == BOND_BONDING) {
					LOG.info("New state: bonding");
				} else if (state == BOND_BONDED) {
					LOG.info("New state: bonded");
				}
			}
		}
	}
}
