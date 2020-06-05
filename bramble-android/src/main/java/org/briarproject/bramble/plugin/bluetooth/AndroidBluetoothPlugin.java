package org.briarproject.bramble.plugin.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.briarproject.bramble.api.io.TimeoutMonitor;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.util.AndroidUtils;
import org.briarproject.bramble.util.IoUtils;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED;
import static android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_STARTED;
import static android.bluetooth.BluetoothAdapter.ACTION_SCAN_MODE_CHANGED;
import static android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED;
import static android.bluetooth.BluetoothAdapter.EXTRA_SCAN_MODE;
import static android.bluetooth.BluetoothAdapter.EXTRA_STATE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_NONE;
import static android.bluetooth.BluetoothAdapter.STATE_OFF;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static android.bluetooth.BluetoothDevice.ACTION_FOUND;
import static android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE;
import static android.bluetooth.BluetoothDevice.EXTRA_DEVICE;
import static android.os.Build.VERSION.SDK_INT;
import static java.util.Collections.shuffle;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.PrivacyUtils.scrubMacAddress;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class AndroidBluetoothPlugin extends BluetoothPlugin<BluetoothServerSocket> {

	private static final Logger LOG =
			getLogger(AndroidBluetoothPlugin.class.getName());

	private static final int MAX_DISCOVERY_MS = 10_000;

	private final AndroidExecutor androidExecutor;
	private final Context appContext;
	private final Clock clock;

	private volatile boolean wasEnabledByUs = false;
	private volatile BluetoothStateReceiver receiver = null;

	// Non-null if the plugin started successfully
	private volatile BluetoothAdapter adapter = null;

	AndroidBluetoothPlugin(BluetoothConnectionLimiter connectionLimiter,
			TimeoutMonitor timeoutMonitor, Executor ioExecutor,
			SecureRandom secureRandom, AndroidExecutor androidExecutor,
			Context appContext, Clock clock, Backoff backoff,
			PluginCallback callback, int maxLatency, int maxIdleTime) {
		super(connectionLimiter, timeoutMonitor, ioExecutor, secureRandom,
				backoff, callback, maxLatency, maxIdleTime);
		this.androidExecutor = androidExecutor;
		this.appContext = appContext;
		this.clock = clock;
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
		IoUtils.tryToClose(ss, LOG, WARNING);
	}

	@Override
	DuplexTransportConnection acceptConnection(BluetoothServerSocket ss)
			throws IOException {
		return wrapSocket(ss.accept());
	}

	private DuplexTransportConnection wrapSocket(BluetoothSocket s)
			throws IOException {
		return new AndroidBluetoothTransportConnection(this, connectionLimiter,
				timeoutMonitor, appContext, s);
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
			IoUtils.tryToClose(s, LOG, WARNING);
			throw e;
		}
	}

	@Override
	@Nullable
	DuplexTransportConnection discoverAndConnect(String uuid) {
		if (adapter == null) return null;
		for (String address : discoverDevices()) {
			try {
				if (LOG.isLoggable(INFO))
					LOG.info("Connecting to " + scrubMacAddress(address));
				return connectTo(address, uuid);
			} catch (IOException e) {
				if (LOG.isLoggable(INFO)) {
					LOG.info("Could not connect to "
							+ scrubMacAddress(address));
				}
			}
		}
		LOG.info("Could not connect to any devices");
		return null;
	}

	private Collection<String> discoverDevices() {
		List<String> addresses = new ArrayList<>();
		BlockingQueue<Intent> intents = new LinkedBlockingQueue<>();
		DiscoveryReceiver receiver = new DiscoveryReceiver(intents);
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_DISCOVERY_STARTED);
		filter.addAction(ACTION_DISCOVERY_FINISHED);
		filter.addAction(ACTION_FOUND);
		appContext.registerReceiver(receiver, filter);
		try {
			if (adapter.startDiscovery()) {
				long now = clock.currentTimeMillis();
				long end = now + MAX_DISCOVERY_MS;
				while (now < end) {
					Intent i = intents.poll(end - now, MILLISECONDS);
					if (i == null) break;
					String action = i.getAction();
					if (ACTION_DISCOVERY_STARTED.equals(action)) {
						LOG.info("Discovery started");
					} else if (ACTION_DISCOVERY_FINISHED.equals(action)) {
						LOG.info("Discovery finished");
						break;
					} else if (ACTION_FOUND.equals(action)) {
						BluetoothDevice d = i.getParcelableExtra(EXTRA_DEVICE);
						// Ignore Bluetooth LE devices
						if (SDK_INT < 18 || d.getType() != DEVICE_TYPE_LE) {
							String address = d.getAddress();
							if (LOG.isLoggable(INFO))
								LOG.info("Discovered " +
										scrubMacAddress(address));
							if (!addresses.contains(address))
								addresses.add(address);
						}
					}
					now = clock.currentTimeMillis();
				}
			} else {
				LOG.info("Could not start discovery");
			}
		} catch (InterruptedException e) {
			LOG.info("Interrupted while discovering devices");
			Thread.currentThread().interrupt();
		} finally {
			LOG.info("Cancelling discovery");
			adapter.cancelDiscovery();
			appContext.unregisterReceiver(receiver);
		}
		// Shuffle the addresses so we don't always try the same one first
		shuffle(addresses);
		return addresses;
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

	private static class DiscoveryReceiver extends BroadcastReceiver {

		private final BlockingQueue<Intent> intents;

		private DiscoveryReceiver(BlockingQueue<Intent> intents) {
			this.intents = intents;
		}

		@Override
		public void onReceive(Context ctx, Intent intent) {
			intents.add(intent);
		}
	}
}
