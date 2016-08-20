package org.briarproject.plugins.droidtooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.briarproject.android.api.AndroidExecutor;
import org.briarproject.android.util.AndroidUtils;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.keyagreement.KeyAgreementConnection;
import org.briarproject.api.keyagreement.KeyAgreementListener;
import org.briarproject.api.keyagreement.TransportDescriptor;
import org.briarproject.api.plugins.Backoff;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.properties.TransportProperties;
import org.briarproject.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
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

	private static final String PROP_ADDRESS = "address";
	private static final String PROP_UUID = "uuid";

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
	public boolean start() throws IOException {
		if (used.getAndSet(true)) throw new IllegalStateException();
		// BluetoothAdapter.getDefaultAdapter() must be called on a thread
		// with a message queue, so submit it to the AndroidExecutor
		try {
			adapter = androidExecutor.runOnBackgroundThread(
					new Callable<BluetoothAdapter>() {
						@Override
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
			@Override
			public void run() {
				if (!isRunning()) return;
				String address = AndroidUtils.getBluetoothAddress(appContext,
						adapter);
				if (LOG.isLoggable(INFO))
					LOG.info("Local address " + address);
				if (!StringUtils.isNullOrEmpty(address)) {
					// Advertise the Bluetooth address to contacts
					TransportProperties p = new TransportProperties();
					p.put(PROP_ADDRESS, address);
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
				backoff.reset();
				callback.transportEnabled();
				acceptContactConnections();
			}
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
			backoff.reset();
			callback.incomingConnectionCreated(wrapSocket(s));
		}
	}

	private DuplexTransportConnection wrapSocket(BluetoothSocket s) {
		return new DroidtoothTransportConnection(this, s);
	}

	@Override
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
			final ContactId c = e.getKey();
			if (connected.contains(c)) continue;
			final String address = e.getValue().get(PROP_ADDRESS);
			if (StringUtils.isNullOrEmpty(address)) continue;
			final String uuid = e.getValue().get(PROP_UUID);
			if (StringUtils.isNullOrEmpty(uuid)) continue;
			ioExecutor.execute(new Runnable() {
				@Override
				public void run() {
					if (!running) return;
					BluetoothSocket s = connect(address, uuid);
					if (s != null) {
						backoff.reset();
						callback.outgoingConnectionCreated(c, wrapSocket(s));
					}
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

	@Override
	public DuplexTransportConnection createConnection(ContactId c) {
		if (!isRunning()) return null;
		TransportProperties p = callback.getRemoteProperties().get(c);
		if (p == null) return null;
		String address = p.get(PROP_ADDRESS);
		if (StringUtils.isNullOrEmpty(address)) return null;
		String uuid = p.get(PROP_UUID);
		if (StringUtils.isNullOrEmpty(uuid)) return null;
		BluetoothSocket s = connect(address, uuid);
		if (s == null) return null;
		return new DroidtoothTransportConnection(this, s);
	}

	@Override
	public boolean supportsInvitations() {
		return true;
	}

	@Override
	public DuplexTransportConnection createInvitationConnection(PseudoRandom r,
			long timeout, boolean alice) {
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
		// Create the background tasks
		CompletionService<BluetoothSocket> complete =
				new ExecutorCompletionService<>(ioExecutor);
		List<Future<BluetoothSocket>> futures = new ArrayList<>();
		if (alice) {
			// Return the first connected socket
			futures.add(complete.submit(new ListeningTask(ss)));
			futures.add(complete.submit(new DiscoveryTask(uuid.toString())));
		} else {
			// Return the first socket with readable data
			futures.add(complete.submit(new ReadableTask(
					new ListeningTask(ss))));
			futures.add(complete.submit(new ReadableTask(
					new DiscoveryTask(uuid.toString()))));
		}
		BluetoothSocket chosen = null;
		try {
			Future<BluetoothSocket> f = complete.poll(timeout, MILLISECONDS);
			if (f == null) return null; // No task completed within the timeout
			chosen = f.get();
			return new DroidtoothTransportConnection(this, chosen);
		} catch (InterruptedException e) {
			LOG.info("Interrupted while exchanging invitations");
			Thread.currentThread().interrupt();
			return null;
		} catch (ExecutionException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		} finally {
			// Closing the socket will terminate the listener task
			tryToClose(ss);
			closeSockets(futures, chosen);
		}
	}

	private void closeSockets(final List<Future<BluetoothSocket>> futures,
			final BluetoothSocket chosen) {
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				for (Future<BluetoothSocket> f : futures) {
					try {
						if (f.cancel(true)) {
							LOG.info("Cancelled task");
						} else {
							BluetoothSocket s = f.get();
							if (s != null && s != chosen) {
								LOG.info("Closing unwanted socket");
								s.close();
							}
						}
					} catch (InterruptedException e) {
						LOG.info("Interrupted while closing sockets");
						return;
					} catch (ExecutionException | IOException e) {
						if (LOG.isLoggable(INFO)) LOG.info(e.toString());
					}
				}
			}
		});
	}

	@Override
	public boolean supportsKeyAgreement() {
		return true;
	}

	@Override
	public KeyAgreementListener createKeyAgreementListener(byte[] commitment) {
		if (!isRunning()) return null;
		// No truncation necessary because COMMIT_LENGTH = 16
		UUID uuid = UUID.nameUUIDFromBytes(commitment);
		if (LOG.isLoggable(INFO)) LOG.info("Key agreement UUID " + uuid);
		// Bind a server socket for receiving invitation connections
		BluetoothServerSocket ss;
		try {
			ss = InsecureBluetooth.listen(adapter, "RFCOMM", uuid);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
		TransportProperties p = new TransportProperties();
		String address = AndroidUtils.getBluetoothAddress(appContext, adapter);
		if (!StringUtils.isNullOrEmpty(address))
			p.put(PROP_ADDRESS, address);
		TransportDescriptor d = new TransportDescriptor(ID, p);
		return new BluetoothKeyAgreementListener(d, ss);
	}

	@Override
	public DuplexTransportConnection createKeyAgreementConnection(
			byte[] commitment, TransportDescriptor d, long timeout) {
		if (!isRunning()) return null;
		if (!ID.equals(d.getIdentifier())) return null;
		TransportProperties p = d.getProperties();
		if (p == null) return null;
		String address = p.get(PROP_ADDRESS);
		if (StringUtils.isNullOrEmpty(address)) return null;
		// No truncation necessary because COMMIT_LENGTH = 16
		UUID uuid = UUID.nameUUIDFromBytes(commitment);
		if (LOG.isLoggable(INFO))
			LOG.info("Connecting to key agreement UUID " + uuid);
		BluetoothSocket s = connect(address, uuid.toString());
		if (s == null) return null;
		return new DroidtoothTransportConnection(this, s);
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

	private class DiscoveryTask implements Callable<BluetoothSocket> {

		private final String uuid;

		private DiscoveryTask(String uuid) {
			this.uuid = uuid;
		}

		@Override
		public BluetoothSocket call() throws Exception {
			// Repeat discovery until we connect or get interrupted
			while (true) {
				// Discover nearby devices
				LOG.info("Discovering nearby devices");
				List<String> addresses = discoverDevices();
				if (addresses.isEmpty()) {
					LOG.info("No devices discovered");
					continue;
				}
				// Connect to any device with the right UUID
				for (String address : addresses) {
					BluetoothSocket s = connect(address, uuid);
					if (s != null) {
						LOG.info("Outgoing connection");
						return s;
					}
				}
			}
		}

		private List<String> discoverDevices() throws InterruptedException {
			IntentFilter filter = new IntentFilter();
			filter.addAction(FOUND);
			filter.addAction(DISCOVERY_FINISHED);
			DiscoveryReceiver disco = new DiscoveryReceiver();
			appContext.registerReceiver(disco, filter);
			LOG.info("Starting discovery");
			adapter.startDiscovery();
			return disco.waitForAddresses();
		}
	}

	private static class DiscoveryReceiver extends BroadcastReceiver {

		private final CountDownLatch finished = new CountDownLatch(1);
		private final List<String> addresses = new ArrayList<>();

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

		private List<String> waitForAddresses() throws InterruptedException {
			finished.await();
			Collections.shuffle(addresses);
			return Collections.unmodifiableList(addresses);
		}
	}

	private static class ListeningTask implements Callable<BluetoothSocket> {

		private final BluetoothServerSocket serverSocket;

		private ListeningTask(BluetoothServerSocket serverSocket) {
			this.serverSocket = serverSocket;
		}

		@Override
		public BluetoothSocket call() throws IOException {
			BluetoothSocket s = serverSocket.accept();
			LOG.info("Incoming connection");
			return s;
		}
	}

	private static class ReadableTask implements Callable<BluetoothSocket> {

		private final Callable<BluetoothSocket> connectionTask;

		private ReadableTask(Callable<BluetoothSocket> connectionTask) {
			this.connectionTask = connectionTask;
		}

		@Override
		public BluetoothSocket call() throws Exception {
			BluetoothSocket s = connectionTask.call();
			InputStream in = s.getInputStream();
			while (in.available() == 0) {
				LOG.info("Waiting for data");
				Thread.sleep(1000);
			}
			LOG.info("Data available");
			return s;
		}
	}

	private class BluetoothKeyAgreementListener extends KeyAgreementListener {

		private final BluetoothServerSocket ss;

		BluetoothKeyAgreementListener(TransportDescriptor descriptor,
				BluetoothServerSocket ss) {
			super(descriptor);
			this.ss = ss;
		}

		@Override
		public Callable<KeyAgreementConnection> listen() {
			return new Callable<KeyAgreementConnection>() {
				@Override
				public KeyAgreementConnection call() throws IOException {
					BluetoothSocket s = ss.accept();
					if (LOG.isLoggable(INFO))
						LOG.info(ID.getString() + ": Incoming connection");
					return new KeyAgreementConnection(
							new DroidtoothTransportConnection(
									DroidtoothPlugin.this, s), ID);
				}
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
