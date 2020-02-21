package org.briarproject.bramble.plugin.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.os.Parcelable;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.DiscoveryHandler;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.util.AndroidUtils;
import org.briarproject.bramble.util.IoUtils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
import static android.bluetooth.BluetoothDevice.ACTION_UUID;
import static android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE;
import static android.bluetooth.BluetoothDevice.EXTRA_DEVICE;
import static android.bluetooth.BluetoothDevice.EXTRA_UUID;
import static android.os.Build.VERSION.SDK_INT;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.shuffle;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_ADDRESS;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_UUID;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.PrivacyUtils.scrubMacAddress;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class AndroidBluetoothPlugin extends BluetoothPlugin<BluetoothServerSocket> {

	private static final Logger LOG =
			getLogger(AndroidBluetoothPlugin.class.getName());

	private static final int MIN_DEVICE_DISCOVERY_MS = 2_000;
	private static final int MAX_DEVICE_DISCOVERY_MS = 30_000;
	private static final int MAX_SERVICE_DISCOVERY_MS = 15_000;

	private final AndroidExecutor androidExecutor;
	private final Context appContext;
	private final Clock clock;

	private volatile boolean wasEnabledByUs = false;
	private volatile BluetoothStateReceiver receiver = null;

	// Non-null if the plugin started successfully
	private volatile BluetoothAdapter adapter = null;

	AndroidBluetoothPlugin(BluetoothConnectionLimiter connectionLimiter,
			Executor ioExecutor, AndroidExecutor androidExecutor,
			Context appContext, SecureRandom secureRandom, Clock clock,
			Backoff backoff, PluginCallback callback, int maxLatency) {
		super(connectionLimiter, ioExecutor, secureRandom, backoff, callback,
				maxLatency);
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
			cancelDiscoverability();
			if (adapter.disable()) LOG.info("Disabling Bluetooth");
			else LOG.info("Could not disable Bluetooth");
			wasEnabledByUs = false;
		}
	}

	private void cancelDiscoverability() {
		if (adapter.getScanMode() == SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			try {
				Method setDiscoverableTimeout = BluetoothAdapter.class
						.getDeclaredMethod("setDiscoverableTimeout", int.class);
				setDiscoverableTimeout.setAccessible(true);
				setDiscoverableTimeout.invoke(adapter, 1);
				LOG.info("Cancelled discoverability");
			} catch (NoSuchMethodException e) {
				logException(LOG, WARNING, e);
			} catch (IllegalAccessException e) {
				logException(LOG, WARNING, e);
			} catch (InvocationTargetException e) {
				logException(LOG, WARNING, e);
			}
		}
	}

	@Override
	void setEnabledByUs() {
		wasEnabledByUs = true;
	}

	@Override
	void onAdapterDisabled() {
		super.onAdapterDisabled();
		wasEnabledByUs = false;
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
			IoUtils.tryToClose(s, LOG, WARNING);
			throw e;
		}
	}

	@Override
	@Nullable
	DuplexTransportConnection discoverAndConnect(String uuid) {
		if (adapter == null) return null;
		for (BluetoothDevice d : discoverDevices()) {
			String address = d.getAddress();
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

	@Override
	public boolean supportsDiscovery() {
		return true;
	}

	@Override
	public void discoverPeers(
			List<Pair<TransportProperties, DiscoveryHandler>> properties) {
		// Discover all nearby devices
		List<BluetoothDevice> devices = discoverDevices();
		if (devices.isEmpty()) {
			LOG.info("No devices discovered");
			return;
		}

		List<Pair<TransportProperties, DiscoveryHandler>> discovered =
				new ArrayList<>();
		Map<String, Pair<TransportProperties, DiscoveryHandler>> byUuid =
				new HashMap<>();
		Map<String, Pair<TransportProperties, DiscoveryHandler>> byAddress =
				new HashMap<>();
		for (Pair<TransportProperties, DiscoveryHandler> pair : properties) {
			TransportProperties p = pair.getFirst();
			String uuid = p.get(PROP_UUID);
			if (!isNullOrEmpty(uuid)) {
				byUuid.put(uuid, pair);
				String address = p.get(PROP_ADDRESS);
				if (!isNullOrEmpty(address)) byAddress.put(address, pair);
			}
		}

		List<BluetoothDevice> unknown = new ArrayList<>(devices);
		for (BluetoothDevice d : devices) {
			Pair<TransportProperties, DiscoveryHandler> pair =
					byAddress.remove(d.getAddress());
			if (pair == null) {
				// Try cached UUIDs
				for (String uuid : getUuids(d)) {
					pair = byUuid.remove(uuid);
					if (pair != null) {
						if (LOG.isLoggable(INFO)) {
							LOG.info("Matched "
									+ scrubMacAddress(d.getAddress())
									+ " by cached UUID");
						}
						TransportProperties p =
								new TransportProperties(pair.getFirst());
						p.put(PROP_ADDRESS, d.getAddress());
						discovered.add(new Pair<>(p, pair.getSecond()));
						unknown.remove(d);
						break;
					}
				}
			} else {
				if (LOG.isLoggable(INFO)) {
					LOG.info("Matched " + scrubMacAddress(d.getAddress())
							+ " by address");
				}
				discovered.add(pair);
				unknown.remove(d);
			}
		}
		if (unknown.isEmpty()) {
			LOG.info("All discovered devices are known, not fetching UUIDs");
			return;
		}
		// Fetch up-to-date UUIDs
		if (LOG.isLoggable(INFO))
			LOG.info("Fetching UUIDs for " + unknown.size() + " devices");
		BlockingQueue<Intent> intents = new LinkedBlockingQueue<>();
		QueueingReceiver receiver = new QueueingReceiver(intents);
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_UUID);
		appContext.registerReceiver(receiver, filter);
		try {
			List<BluetoothDevice> pending = new ArrayList<>();
			for (BluetoothDevice d : unknown) {
				if (d.fetchUuidsWithSdp()) {
					if (LOG.isLoggable(INFO)) {
						LOG.info("Fetching UUIDs for "
								+ scrubMacAddress(d.getAddress()));
					}
					pending.add(d);
				} else {
					if (LOG.isLoggable(INFO)) {
						LOG.info("Failed to fetch UUIDs for "
								+ scrubMacAddress(d.getAddress()));
					}
				}
			}
			long now = clock.currentTimeMillis();
			long end = now + MAX_SERVICE_DISCOVERY_MS;
			while (now < end && !pending.isEmpty()) {
				Intent i = intents.poll(end - now, MILLISECONDS);
				if (i == null) break;
				BluetoothDevice d = requireNonNull(
						i.getParcelableExtra(EXTRA_DEVICE));
				if (LOG.isLoggable(INFO)) {
					LOG.info("Fetched UUIDs for "
							+ scrubMacAddress(d.getAddress()));
				}
				Set<String> uuids = getUuids(d);
				Parcelable[] extra = i.getParcelableArrayExtra(EXTRA_UUID);
				if (extra != null) {
					for (Parcelable p : extra) {
						uuids.addAll(getUuidStrings((ParcelUuid) p));
					}
				}
				for (String uuid : uuids) {
					Pair<TransportProperties, DiscoveryHandler> pair =
							byUuid.remove(uuid);
					if (pair != null) {
						if (LOG.isLoggable(INFO)) {
							LOG.info("Matched "
									+ scrubMacAddress(d.getAddress())
									+ " by fetched UUID");
						}
						TransportProperties p =
								new TransportProperties(pair.getFirst());
						p.put(PROP_ADDRESS, d.getAddress());
						discovered.add(new Pair<>(p, pair.getSecond()));
						break;
					}
				}
				pending.remove(d);
				now = clock.currentTimeMillis();
			}
			if (LOG.isLoggable(INFO)) {
				if (pending.isEmpty()) {
					LOG.info("Finished fetching UUIDs");
				} else {
					LOG.info("Failed to fetch UUIDs for " + pending.size()
							+ " devices");
				}
			}
		} catch (InterruptedException e) {
			LOG.info("Interrupted while fetching UUIDs");
			Thread.currentThread().interrupt();
		} finally {
			appContext.unregisterReceiver(receiver);
		}

		if (LOG.isLoggable(INFO)) {
			LOG.info("Discovered " + discovered.size() + " contacts");
		}
		for (Pair<TransportProperties, DiscoveryHandler> pair : discovered) {
			pair.getSecond().handleDevice(pair.getFirst());
		}
	}

	private Set<String> getUuids(BluetoothDevice d) {
		Set<String> strings = new TreeSet<>();
		ParcelUuid[] uuids = d.getUuids();
		if (uuids == null) return strings;
		for (ParcelUuid u : uuids) strings.addAll(getUuidStrings(u));
		return strings;
	}

	// Workaround for https://code.google.com/p/android/issues/detail?id=197341
	private List<String> getUuidStrings(ParcelUuid u) {
		UUID forwards = u.getUuid();
		ByteBuffer buf = ByteBuffer.allocate(16);
		buf.putLong(forwards.getLeastSignificantBits());
		buf.putLong(forwards.getMostSignificantBits());
		buf.rewind();
		buf.order(LITTLE_ENDIAN);
		UUID backwards = new UUID(buf.getLong(), buf.getLong());
		return asList(forwards.toString(), backwards.toString());
	}

	private List<BluetoothDevice> discoverDevices() {
		if (adapter.isDiscovering()) {
			LOG.info("Already discovering");
			return emptyList();
		}
		List<BluetoothDevice> devices = new ArrayList<>();
		BlockingQueue<Intent> intents = new LinkedBlockingQueue<>();
		QueueingReceiver receiver = new QueueingReceiver(intents);
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_DISCOVERY_STARTED);
		filter.addAction(ACTION_DISCOVERY_FINISHED);
		filter.addAction(ACTION_FOUND);
		appContext.registerReceiver(receiver, filter);
		try {
			if (adapter.startDiscovery()) {
				long start = clock.currentTimeMillis();
				long end = start + MAX_DEVICE_DISCOVERY_MS;
				long now = start;
				while (now < end) {
					Intent i = intents.poll(end - now, MILLISECONDS);
					if (i == null) break;
					String action = i.getAction();
					if (ACTION_DISCOVERY_STARTED.equals(action)) {
						LOG.info("Discovery started");
					} else if (ACTION_DISCOVERY_FINISHED.equals(action)) {
						LOG.info("Discovery finished");
						now = clock.currentTimeMillis();
						if (now - start < MIN_DEVICE_DISCOVERY_MS) {
							LOG.info("Discovery finished quickly, retrying");
							if (!adapter.startDiscovery()) {
								LOG.info("Could not restart discovery");
								break;
							}
						} else {
							break;
						}
					} else if (ACTION_FOUND.equals(action)) {
						BluetoothDevice d = requireNonNull(
								i.getParcelableExtra(EXTRA_DEVICE));
						// Ignore Bluetooth LE devices
						if (SDK_INT < 18 || d.getType() != DEVICE_TYPE_LE) {
							if (LOG.isLoggable(INFO)) {
								LOG.info("Discovered "
										+ scrubMacAddress(d.getAddress()));
							}
							if (!devices.contains(d)) devices.add(d);
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
		// Shuffle the devices so we don't always try the same one first
		shuffle(devices);
		return devices;
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

	private static class QueueingReceiver extends BroadcastReceiver {

		private final BlockingQueue<Intent> intents;

		private QueueingReceiver(BlockingQueue<Intent> intents) {
			this.intents = intents;
		}

		@Override
		public void onReceive(Context ctx, Intent intent) {
			intents.add(intent);
		}
	}
}
