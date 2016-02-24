package org.briarproject.plugins.droidtooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.briarproject.android.util.AndroidUtils;
import org.briarproject.api.TransportId;
import org.briarproject.api.android.AndroidExecutor;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.properties.TransportProperties;
import org.briarproject.api.system.Clock;
import org.briarproject.util.LatchedReference;
import org.briarproject.util.StringUtils;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static android.bluetooth.BluetoothAdapter.ACTION_SCAN_MODE_CHANGED;
import static android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED;
import static android.bluetooth.BluetoothAdapter.EXTRA_SCAN_MODE;
import static android.bluetooth.BluetoothAdapter.EXTRA_STATE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_NONE;
import static android.bluetooth.BluetoothAdapter.STATE_OFF;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static android.bluetooth.BluetoothDevice.EXTRA_DEVICE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

class DroidtoothPlugin implements DuplexPlugin {

	// Share an ID with the J2SE Bluetooth plugin
	static final TransportId ID = new TransportId("bt");

	private static final Logger LOG =
			Logger.getLogger(DroidtoothPlugin.class.getName());
	private static final int UUID_BYTES = 16;
	private static final String FOUND =
			"android.bluetooth.device.action.FOUND";
	private static final String DISCOVERY_FINISHED =
			"android.bluetooth.adapter.action.DISCOVERY_FINISHED";

	private final Executor ioExecutor;
	private final AndroidExecutor androidExecutor;
	private final Context appContext;
	private final SecureRandom secureRandom;
	private final Clock clock;
	private final DuplexPluginCallback callback;
	private final int maxLatency, pollingInterval;

	private volatile boolean running = false;
	private volatile boolean wasEnabledByUs = false;
	private volatile BluetoothStateReceiver receiver = null;
	private volatile BluetoothServerSocket socket = null;

	// Non-null if the plugin started successfully
	private volatile BluetoothAdapter adapter = null;

	DroidtoothPlugin(Executor ioExecutor, AndroidExecutor androidExecutor,
			Context appContext, SecureRandom secureRandom, Clock clock,
			DuplexPluginCallback callback, int maxLatency,
			int pollingInterval) {
		this.ioExecutor = ioExecutor;
		this.androidExecutor = androidExecutor;
		this.appContext = appContext;
		this.secureRandom = secureRandom;
		this.clock = clock;
		this.callback = callback;
		this.maxLatency = maxLatency;
		this.pollingInterval = pollingInterval;
	}

	public TransportId getId() {
		return ID;
	}

	public int getMaxLatency() {
		return maxLatency;
	}

	public int getMaxIdleTime() {
		// Bluetooth detects dead connections so we don't need keepalives
		return Integer.MAX_VALUE;
	}

	public boolean start() throws IOException {
		// BluetoothAdapter.getDefaultAdapter() must be called on a thread
		// with a message queue, so submit it to the AndroidExecutor
		try {
			adapter = androidExecutor.submit(new Callable<BluetoothAdapter>() {
				public BluetoothAdapter call() throws Exception {
					return BluetoothAdapter.getDefaultAdapter();
				}
			}).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while getting BluetoothAdapter");
		} catch (ExecutionException e) {
			throw new IOException(e);
		}
		if (adapter == null) {
			LOG.info("Bluetooth is not supported");
			return false;
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
			if (callback.getSettings().getBoolean("enable", false)) {
				wasEnabledByUs = true;
				if (adapter.enable()) LOG.info("Enabling Bluetooth");
				else LOG.info("Could not enable Bluetooth");
			} else {
				LOG.info("Not enabling Bluetooth");
			}
		}
		return true;
	}

	private void bind() {
		ioExecutor.execute(new Runnable() {
			public void run() {
				if (!isRunning()) return;
				String address = AndroidUtils.getBluetoothAddress(appContext,
						adapter);
				if (LOG.isLoggable(INFO))
					LOG.info("Local address " + address);
				if (!StringUtils.isNullOrEmpty(address)) {
					// Advertise the Bluetooth address to contacts
					TransportProperties p = new TransportProperties();
					p.put("address", address);
					callback.mergeLocalProperties(p);
				}
				// Bind a server socket to accept connections from contacts
				BluetoothServerSocket ss;
				try {
					ss = InsecureBluetooth.listen(adapter, "RFCOMM", getUuid());
				} catch (IOException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					return;
				}
				if (!isRunning()) {
					tryToClose(ss);
					return;
				}
				LOG.info("Socket bound");
				socket = ss;
				callback.transportEnabled();
				acceptContactConnections();
			}
		});
	}

	private UUID getUuid() {
		String uuid = callback.getLocalProperties().get("uuid");
		if (uuid == null) {
			byte[] random = new byte[UUID_BYTES];
			secureRandom.nextBytes(random);
			uuid = UUID.nameUUIDFromBytes(random).toString();
			TransportProperties p = new TransportProperties();
			p.put("uuid", uuid);
			callback.mergeLocalProperties(p);
		}
		return UUID.fromString(uuid);
	}

	private void tryToClose(BluetoothServerSocket ss) {
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
				LOG.info("Connection from " + address);
			}
			callback.incomingConnectionCreated(wrapSocket(s));
		}
	}

	private DuplexTransportConnection wrapSocket(BluetoothSocket s) {
		return new DroidtoothTransportConnection(this, s);
	}

	public void stop() {
		running = false;
		if (receiver != null) appContext.unregisterReceiver(receiver);
		tryToClose(socket);
		// Disable Bluetooth if we enabled it and it's still enabled
		if (wasEnabledByUs && adapter.isEnabled()) {
			if (adapter.disable()) LOG.info("Disabling Bluetooth");
			else LOG.info("Could not disable Bluetooth");
		}
	}

	public boolean isRunning() {
		return running && adapter.isEnabled();
	}

	public boolean shouldPoll() {
		return true;
	}

	public int getPollingInterval() {
		return pollingInterval;
	}

	public void poll(Collection<ContactId> connected) {
		if (!isRunning()) return;
		// Try to connect to known devices in parallel
		Map<ContactId, TransportProperties> remote =
				callback.getRemoteProperties();
		for (Entry<ContactId, TransportProperties> e : remote.entrySet()) {
			final ContactId c = e.getKey();
			if (connected.contains(c)) continue;
			final String address = e.getValue().get("address");
			if (StringUtils.isNullOrEmpty(address)) continue;
			final String uuid = e.getValue().get("uuid");
			if (StringUtils.isNullOrEmpty(uuid)) continue;
			ioExecutor.execute(new Runnable() {
				public void run() {
					if (!running) return;
					BluetoothSocket s = connect(address, uuid);
					if (s != null)
						callback.outgoingConnectionCreated(c, wrapSocket(s));
				}
			});
		}
	}

	private BluetoothSocket connect(String address, String uuid) {
		// Validate the address
		if (!BluetoothAdapter.checkBluetoothAddress(address)) {
			if (LOG.isLoggable(WARNING))
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
			s = InsecureBluetooth.createSocket(d, u);
			if (LOG.isLoggable(INFO)) LOG.info("Connecting to " + address);
			s.connect();
			if (LOG.isLoggable(INFO)) LOG.info("Connected to " + address);
			return s;
		} catch (IOException e) {
			if (LOG.isLoggable(INFO))
				LOG.info("Failed to connect to " + address);
			tryToClose(s);
			return null;
		}
	}

	private void tryToClose(BluetoothSocket s) {
		try {
			if (s != null) s.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	public DuplexTransportConnection createConnection(ContactId c) {
		if (!isRunning()) return null;
		TransportProperties p = callback.getRemoteProperties().get(c);
		if (p == null) return null;
		String address = p.get("address");
		if (StringUtils.isNullOrEmpty(address)) return null;
		String uuid = p.get("uuid");
		if (StringUtils.isNullOrEmpty(uuid)) return null;
		BluetoothSocket s = connect(address, uuid);
		if (s == null) return null;
		return new DroidtoothTransportConnection(this, s);
	}

	public boolean supportsInvitations() {
		return true;
	}

	public DuplexTransportConnection createInvitationConnection(PseudoRandom r,
			long timeout) {
		if (!isRunning()) return null;
		// Use the invitation codes to generate the UUID
		byte[] b = r.nextBytes(UUID_BYTES);
		UUID uuid = UUID.nameUUIDFromBytes(b);
		if (LOG.isLoggable(INFO)) LOG.info("Invitation UUID " + uuid);
		// Bind a server socket for receiving invitation connections
		BluetoothServerSocket ss;
		try {
			ss = InsecureBluetooth.listen(adapter, "RFCOMM", uuid);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
		// Start the background threads
		LatchedReference<BluetoothSocket> socketLatch =
				new LatchedReference<BluetoothSocket>();
		new DiscoveryThread(socketLatch, uuid.toString(), timeout).start();
		new BluetoothListenerThread(socketLatch, ss).start();
		// Wait for an incoming or outgoing connection
		try {
			BluetoothSocket s = socketLatch.waitForReference(timeout);
			if (s != null) return new DroidtoothTransportConnection(this, s);
		} catch (InterruptedException e) {
			LOG.warning("Interrupted while exchanging invitations");
			Thread.currentThread().interrupt();
		} finally {
			// Closing the socket will terminate the listener thread
			tryToClose(ss);
		}
		return null;
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

	private class DiscoveryThread extends Thread {

		private final LatchedReference<BluetoothSocket> socketLatch;
		private final String uuid;
		private final long timeout;

		private DiscoveryThread(LatchedReference<BluetoothSocket> socketLatch,
				String uuid, long timeout) {
			this.socketLatch = socketLatch;
			this.uuid = uuid;
			this.timeout = timeout;
		}

		@Override
		public void run() {
			long end = clock.currentTimeMillis() + timeout;
			while (!finished(end)) {
				// Discover nearby devices
				LOG.info("Discovering nearby devices");
				List<String> addresses;
				try {
					long now = clock.currentTimeMillis();
					addresses = discoverDevices(end - now);
				} catch (InterruptedException e) {
					LOG.warning("Interrupted while discovering devices");
					Thread.currentThread().interrupt();
					return;
				}
				if (addresses.isEmpty()) {
					LOG.info("No devices discovered");
					continue;
				}
				// Connect to any device with the right UUID
				for (String address : addresses) {
					if (finished(end)) return;
					BluetoothSocket s = connect(address, uuid);
					if (s != null) {
						LOG.info("Outgoing connection");
						if (!socketLatch.set(s)) {
							LOG.info("Closing redundant connection");
							tryToClose(s);
						}
						return;
					}
				}
			}
		}

		private boolean finished(long end) {
			long now = clock.currentTimeMillis();
			return now >= end || !isRunning() || socketLatch.isSet();
		}

		private List<String> discoverDevices(long timeout)
				throws InterruptedException {
			IntentFilter filter = new IntentFilter();
			filter.addAction(FOUND);
			filter.addAction(DISCOVERY_FINISHED);
			DiscoveryReceiver disco = new DiscoveryReceiver();
			appContext.registerReceiver(disco, filter);
			LOG.info("Starting discovery");
			adapter.startDiscovery();
			return disco.waitForAddresses(timeout);
		}
	}

	private static class DiscoveryReceiver extends BroadcastReceiver {

		private final CountDownLatch finished = new CountDownLatch(1);
		private final List<String> addresses = new ArrayList<String>();

		@Override
		public void onReceive(Context ctx, Intent intent) {
			String action = intent.getAction();
			if (action.equals(DISCOVERY_FINISHED)) {
				LOG.info("Discovery finished");
				ctx.unregisterReceiver(this);
				finished.countDown();
			} else if (action.equals(FOUND)) {
				BluetoothDevice d = intent.getParcelableExtra(EXTRA_DEVICE);
				if (LOG.isLoggable(INFO))
					LOG.info("Discovered device: " + d.getAddress());
				addresses.add(d.getAddress());
			}
		}

		private List<String> waitForAddresses(long timeout)
				throws InterruptedException {
			finished.await(timeout, MILLISECONDS);
			return Collections.unmodifiableList(addresses);
		}
	}

	private static class BluetoothListenerThread extends Thread {

		private final LatchedReference<BluetoothSocket> socketLatch;
		private final BluetoothServerSocket serverSocket;

		private BluetoothListenerThread(
				LatchedReference<BluetoothSocket> socketLatch,
				BluetoothServerSocket serverSocket) {
			this.socketLatch = socketLatch;
			this.serverSocket = serverSocket;
		}

		@Override
		public void run() {
			try {
				BluetoothSocket s = serverSocket.accept();
				LOG.info("Incoming connection");
				if (!socketLatch.set(s)) {
					LOG.info("Closing redundant connection");
					s.close();
				}
			} catch (IOException e) {
				// This is expected when the socket is closed
				if (LOG.isLoggable(INFO)) LOG.info(e.toString());
			}
		}
	}
}
