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
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

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

	AndroidBluetoothPlugin(BluetoothConnectionLimiter connectionLimiter,
			Executor ioExecutor, AndroidExecutor androidExecutor,
			Context appContext, SecureRandom secureRandom, Backoff backoff,
			DuplexPluginCallback callback, int maxLatency) {
		super(connectionLimiter, ioExecutor, secureRandom, backoff, callback,
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
			logException(LOG, WARNING, e);
		}
	}

	@Override
	DuplexTransportConnection acceptConnection(BluetoothServerSocket ss)
			throws IOException {
		return wrapSocket(ss.accept());
	}

	private DuplexTransportConnection wrapSocket(BluetoothSocket s) {
		return new AndroidBluetoothTransportConnection(this,
				connectionLimiter, s);
	}

	@Override
	boolean isValidAddress(String address) {
		return BluetoothAdapter.checkBluetoothAddress(address);
	}

	@Override
	DuplexTransportConnection connectTo(String address, String uuid)
			throws IOException {
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
			logException(LOG, WARNING, e);
		}
	}

	private class BluetoothStateReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context ctx, Intent intent) {
			int state = intent.getIntExtra(EXTRA_STATE, 0);
			if (state == STATE_ON) onAdapterEnabled();
			else if (state == STATE_OFF) onAdapterDisabled();
			int scanMode = intent.getIntExtra(EXTRA_SCAN_MODE, 0);
			if (scanMode == SCAN_MODE_NONE) {
				LOG.info("Scan mode: None");
			} else if (scanMode == SCAN_MODE_CONNECTABLE) {
				LOG.info("Scan mode: Connectable");
			} else if (scanMode == SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
				LOG.info("Scan mode: Discoverable");
			}
		}
	}
}
