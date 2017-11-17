package org.briarproject.bramble.plugin.droidtooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.keyagreement.KeyAgreementConnection;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.plugin.event.DisableBluetoothEvent;
import org.briarproject.bramble.api.plugin.event.EnableBluetoothEvent;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.util.AndroidUtils;
import org.briarproject.bramble.util.StringUtils;

import java.io.Closeable;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
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
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.TRANSPORT_ID_BLUETOOTH;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.ID;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PREF_BT_ENABLE;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_ADDRESS;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_UUID;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.UUID_BYTES;
import static org.briarproject.bramble.util.PrivacyUtils.scrubMacAddress;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class DroidtoothPlugin implements DuplexPlugin, EventListener {

	private static final Logger LOG =
			Logger.getLogger(DroidtoothPlugin.class.getName());

	private final Executor ioExecutor;
	private final AndroidExecutor androidExecutor;
	private final Context appContext;
	private final SecureRandom secureRandom;
	private final Backoff backoff;
	private final DuplexPluginCallback callback;
	private final int maxLatency;
	private final AtomicBoolean used = new AtomicBoolean(false);

	private volatile boolean running = false;
	private volatile boolean wasEnabledByUs = false;
	private volatile BluetoothStateReceiver receiver = null;
	private volatile BluetoothServerSocket socket = null;

	// Non-null if the plugin started successfully
	private volatile BluetoothAdapter adapter = null;

	DroidtoothPlugin(Executor ioExecutor, AndroidExecutor androidExecutor,
			Context appContext, SecureRandom secureRandom, Backoff backoff,
			DuplexPluginCallback callback, int maxLatency) {
		this.ioExecutor = ioExecutor;
		this.androidExecutor = androidExecutor;
		this.appContext = appContext;
		this.secureRandom = secureRandom;
		this.backoff = backoff;
		this.callback = callback;
		this.maxLatency = maxLatency;
	}

	@Override
	public TransportId getId() {
		return ID;
	}

	@Override
	public int getMaxLatency() {
		return maxLatency;
	}

	@Override
	public int getMaxIdleTime() {
		// Bluetooth detects dead connections so we don't need keepalives
		return Integer.MAX_VALUE;
	}

	@Override
	public void start() throws PluginException {
		if (used.getAndSet(true)) throw new IllegalStateException();
		// BluetoothAdapter.getDefaultAdapter() must be called on a thread
		// with a message queue, so submit it to the AndroidExecutor
		try {
			adapter = androidExecutor.runOnBackgroundThread(
					BluetoothAdapter::getDefaultAdapter).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOG.warning("Interrupted while getting BluetoothAdapter");
			throw new PluginException(e);
		} catch (ExecutionException e) {
			throw new PluginException(e);
		}
		if (adapter == null) {
			LOG.info("Bluetooth is not supported");
			throw new PluginException();
		}
		running = true;
		// Listen for changes to the Bluetooth state
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_STATE_CHANGED);
		filter.addAction(ACTION_SCAN_MODE_CHANGED);
		receiver = new BluetoothStateReceiver();
		appContext.registerReceiver(receiver, filter);
		// If Bluetooth is enabled, bind a socket
		if (adapter.isEnabled()) {
			bind();
		} else {
			// Enable Bluetooth if settings allow
			if (callback.getSettings().getBoolean(PREF_BT_ENABLE, false)) {
				enableAdapter();
			} else {
				LOG.info("Not enabling Bluetooth");
			}
		}
	}

	private void bind() {
		ioExecutor.execute(() -> {
			if (!isRunning()) return;
			String address = AndroidUtils.getBluetoothAddress(appContext,
					adapter);
			if (LOG.isLoggable(INFO))
				LOG.info("Local address " + scrubMacAddress(address));
			if (!StringUtils.isNullOrEmpty(address)) {
				// Advertise the Bluetooth address to contacts
				TransportProperties p = new TransportProperties();
				p.put(PROP_ADDRESS, address);
				callback.mergeLocalProperties(p);
			}
			// Bind a server socket to accept connections from contacts
			BluetoothServerSocket ss;
			try {
				ss = adapter.listenUsingInsecureRfcommWithServiceRecord(
						"RFCOMM", getUuid());
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
				return;
			}
			if (!isRunning()) {
				tryToClose(ss);
				return;
			}
			LOG.info("Socket bound");
			socket = ss;
			backoff.reset();
			callback.transportEnabled();
			acceptContactConnections();
		});
	}

	private UUID getUuid() {
		String uuid = callback.getLocalProperties().get(PROP_UUID);
		if (uuid == null) {
			byte[] random = new byte[UUID_BYTES];
			secureRandom.nextBytes(random);
			uuid = UUID.nameUUIDFromBytes(random).toString();
			TransportProperties p = new TransportProperties();
			p.put(PROP_UUID, uuid);
			callback.mergeLocalProperties(p);
		}
		return UUID.fromString(uuid);
	}

	private void tryToClose(@Nullable BluetoothServerSocket ss) {
		try {
			if (ss != null) ss.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} finally {
			callback.transportDisabled();
		}
	}

	private void acceptContactConnections() {
		while (isRunning()) {
			BluetoothSocket s;
			try {
				s = socket.accept();
			} catch (IOException e) {
				// This is expected when the socket is closed
				if (LOG.isLoggable(INFO)) LOG.info(e.toString());
				return;
			}
			if (LOG.isLoggable(INFO)) {
				String address = s.getRemoteDevice().getAddress();
				LOG.info("Connection from " + scrubMacAddress(address));
			}
			backoff.reset();
			callback.incomingConnectionCreated(wrapSocket(s));
		}
	}

	private DuplexTransportConnection wrapSocket(BluetoothSocket s) {
		return new DroidtoothTransportConnection(this, s);
	}

	private void enableAdapter() {
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
	public void stop() {
		running = false;
		if (receiver != null) appContext.unregisterReceiver(receiver);
		tryToClose(socket);
		disableAdapter();
	}

	private void disableAdapter() {
		if (adapter != null && adapter.isEnabled() && wasEnabledByUs) {
			if (adapter.disable()) LOG.info("Disabling Bluetooth");
			else LOG.info("Could not disable Bluetooth");
		}
	}

	@Override
	public boolean isRunning() {
		return running && adapter != null && adapter.isEnabled();
	}

	@Override
	public boolean shouldPoll() {
		return true;
	}

	@Override
	public int getPollingInterval() {
		return backoff.getPollingInterval();
	}

	@Override
	public void poll(Collection<ContactId> connected) {
		if (!isRunning()) return;
		backoff.increment();
		// Try to connect to known devices in parallel
		Map<ContactId, TransportProperties> remote =
				callback.getRemoteProperties();
		for (Entry<ContactId, TransportProperties> e : remote.entrySet()) {
			ContactId c = e.getKey();
			if (connected.contains(c)) continue;
			String address = e.getValue().get(PROP_ADDRESS);
			if (StringUtils.isNullOrEmpty(address)) continue;
			String uuid = e.getValue().get(PROP_UUID);
			if (StringUtils.isNullOrEmpty(uuid)) continue;
			ioExecutor.execute(() -> {
				if (!running) return;
				BluetoothSocket s = connect(address, uuid);
				if (s != null) {
					backoff.reset();
					callback.outgoingConnectionCreated(c, wrapSocket(s));
				}
			});
		}
	}

	@Nullable
	private BluetoothSocket connect(String address, String uuid) {
		// Validate the address
		if (!BluetoothAdapter.checkBluetoothAddress(address)) {
			if (LOG.isLoggable(WARNING))
				// not scrubbing here to be able to figure out the problem
				LOG.warning("Invalid address " + address);
			return null;
		}
		// Validate the UUID
		UUID u;
		try {
			u = UUID.fromString(uuid);
		} catch (IllegalArgumentException e) {
			if (LOG.isLoggable(WARNING)) LOG.warning("Invalid UUID " + uuid);
			return null;
		}
		// Try to connect
		BluetoothDevice d = adapter.getRemoteDevice(address);
		BluetoothSocket s = null;
		try {
			s = d.createInsecureRfcommSocketToServiceRecord(u);
			if (LOG.isLoggable(INFO))
				LOG.info("Connecting to " + scrubMacAddress(address));
			s.connect();
			if (LOG.isLoggable(INFO))
				LOG.info("Connected to " + scrubMacAddress(address));
			return s;
		} catch (IOException e) {
			if (LOG.isLoggable(INFO)) {
				LOG.info("Failed to connect to " + scrubMacAddress(address)
						+ ": " + e);
			}
			tryToClose(s);
			return null;
		}
	}

	private void tryToClose(@Nullable Closeable c) {
		try {
			if (c != null) c.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	@Override
	public DuplexTransportConnection createConnection(ContactId c) {
		if (!isRunning()) return null;
		TransportProperties p = callback.getRemoteProperties(c);
		String address = p.get(PROP_ADDRESS);
		if (StringUtils.isNullOrEmpty(address)) return null;
		String uuid = p.get(PROP_UUID);
		if (StringUtils.isNullOrEmpty(uuid)) return null;
		BluetoothSocket s = connect(address, uuid);
		if (s == null) return null;
		return new DroidtoothTransportConnection(this, s);
	}

	@Override
	public boolean supportsKeyAgreement() {
		return true;
	}

	@Override
	public KeyAgreementListener createKeyAgreementListener(byte[] commitment) {
		if (!isRunning()) return null;
		// There's no point listening if we can't discover our own address
		String address = AndroidUtils.getBluetoothAddress(appContext, adapter);
		if (address.isEmpty()) return null;
		// No truncation necessary because COMMIT_LENGTH = 16
		UUID uuid = UUID.nameUUIDFromBytes(commitment);
		if (LOG.isLoggable(INFO)) LOG.info("Key agreement UUID " + uuid);
		// Bind a server socket for receiving key agreement connections
		BluetoothServerSocket ss;
		try {
			ss = adapter.listenUsingInsecureRfcommWithServiceRecord(
					"RFCOMM", uuid);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
		BdfList descriptor = new BdfList();
		descriptor.add(TRANSPORT_ID_BLUETOOTH);
		descriptor.add(StringUtils.macToBytes(address));
		return new BluetoothKeyAgreementListener(descriptor, ss);
	}

	@Override
	public DuplexTransportConnection createKeyAgreementConnection(
			byte[] commitment, BdfList descriptor, long timeout) {
		if (!isRunning()) return null;
		String address;
		try {
			address = parseAddress(descriptor);
		} catch (FormatException e) {
			LOG.info("Invalid address in key agreement descriptor");
			return null;
		}
		// No truncation necessary because COMMIT_LENGTH = 16
		UUID uuid = UUID.nameUUIDFromBytes(commitment);
		if (LOG.isLoggable(INFO))
			LOG.info("Connecting to key agreement UUID " + uuid);
		BluetoothSocket s = connect(address, uuid.toString());
		if (s == null) return null;
		return new DroidtoothTransportConnection(this, s);
	}

	private String parseAddress(BdfList descriptor) throws FormatException {
		byte[] mac = descriptor.getRaw(1);
		if (mac.length != 6) throw new FormatException();
		return StringUtils.macToString(mac);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof EnableBluetoothEvent) {
			enableAdapterAsync();
		} else if (e instanceof DisableBluetoothEvent) {
			disableAdapterAsync();
		}
	}

	private void enableAdapterAsync() {
		ioExecutor.execute(this::enableAdapter);
	}

	private void disableAdapterAsync() {
		ioExecutor.execute(this::disableAdapter);
	}

	private class BluetoothStateReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context ctx, Intent intent) {
			int state = intent.getIntExtra(EXTRA_STATE, 0);
			if (state == STATE_ON) {
				LOG.info("Bluetooth enabled");
				bind();
			} else if (state == STATE_OFF) {
				LOG.info("Bluetooth disabled");
				tryToClose(socket);
			}
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

	private class BluetoothKeyAgreementListener extends KeyAgreementListener {

		private final BluetoothServerSocket ss;

		private BluetoothKeyAgreementListener(BdfList descriptor,
				BluetoothServerSocket ss) {
			super(descriptor);
			this.ss = ss;
		}

		@Override
		public Callable<KeyAgreementConnection> listen() {
			return () -> {
				BluetoothSocket s = ss.accept();
				if (LOG.isLoggable(INFO))
					LOG.info(ID.getString() + ": Incoming connection");
				return new KeyAgreementConnection(
						new DroidtoothTransportConnection(
								DroidtoothPlugin.this, s), ID);
			};
		}

		@Override
		public void close() {
			try {
				ss.close();
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}
}
