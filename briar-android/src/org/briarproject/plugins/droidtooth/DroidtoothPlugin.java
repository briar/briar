package org.briarproject.plugins.droidtooth;

import static android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED;
import static android.bluetooth.BluetoothAdapter.EXTRA_STATE;
import static android.bluetooth.BluetoothAdapter.STATE_OFF;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static android.bluetooth.BluetoothDevice.EXTRA_DEVICE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

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

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.android.AndroidExecutor;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.system.Clock;
import org.briarproject.util.LatchedReference;
import org.briarproject.util.StringUtils;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

class DroidtoothPlugin implements DuplexPlugin {

	// Share an ID with the J2SE Bluetooth plugin
	static final byte[] TRANSPORT_ID =
			StringUtils.fromHexString("d99c9313c04417dcf22fc60d12a187ea"
					+ "00a539fd260f08a13a0d8a900cde5e49"
					+ "1b4df2ffd42e40c408f2db7868f518aa");
	static final TransportId ID = new TransportId(TRANSPORT_ID);

	private static final Logger LOG =
			Logger.getLogger(DroidtoothPlugin.class.getName());
	private static final int UUID_BYTES = 16;
	private static final String FOUND = "android.bluetooth.device.action.FOUND";
	private static final String DISCOVERY_FINISHED =
			"android.bluetooth.adapter.action.DISCOVERY_FINISHED";

	private final Executor pluginExecutor;
	private final AndroidExecutor androidExecutor;
	private final Context appContext;
	private final SecureRandom secureRandom;
	private final Clock clock;
	private final DuplexPluginCallback callback;
	private final int maxFrameLength;
	private final long maxLatency, pollingInterval;

	private volatile boolean running = false;
	private volatile boolean wasEnabled = false, isEnabled = false;

	// Non-null if running has ever been true
	private volatile BluetoothAdapter adapter = null;

	DroidtoothPlugin(Executor pluginExecutor, AndroidExecutor androidExecutor,
			Context appContext, SecureRandom secureRandom, Clock clock,
			DuplexPluginCallback callback, int maxFrameLength, long maxLatency,
			long pollingInterval) {
		this.pluginExecutor = pluginExecutor;
		this.androidExecutor = androidExecutor;
		this.appContext = appContext;
		this.secureRandom = secureRandom;
		this.clock = clock;
		this.callback = callback;
		this.maxFrameLength = maxFrameLength;
		this.maxLatency = maxLatency;
		this.pollingInterval = pollingInterval;
	}

	public TransportId getId() {
		return ID;
	}

	public String getName() {
		// Share a name with the J2SE Bluetooth plugin
		return "BLUETOOTH_PLUGIN_NAME";
	}

	public int getMaxFrameLength() {
		return maxFrameLength;
	}

	public long getMaxLatency() {
		return maxLatency;
	}

	public boolean start() throws IOException {
		// BluetoothAdapter.getDefaultAdapter() must be called on a thread
		// with a message queue, so submit it to the AndroidExecutor
		try {
			adapter = androidExecutor.call(new Callable<BluetoothAdapter>() {
				public BluetoothAdapter call() throws Exception {
					return BluetoothAdapter.getDefaultAdapter();
				}
			});
		} catch(InterruptedException e) {
			throw new IOException(e.toString());
		} catch(ExecutionException e) {
			throw new IOException(e.toString());
		}
		if(adapter == null) {
			if(LOG.isLoggable(INFO)) LOG.info("Bluetooth is not supported");
			return false;
		}
		running = true;
		wasEnabled = isEnabled = adapter.isEnabled();
		pluginExecutor.execute(new Runnable() {
			public void run() {
				bind();
			}
		});
		return true;
	}

	private void bind() {
		if(!running) return;
		if(!enableBluetooth()) return;
		if(LOG.isLoggable(INFO))
			LOG.info("Local address " + adapter.getAddress());
		// Advertise the Bluetooth address to contacts
		TransportProperties p = new TransportProperties();
		p.put("address", adapter.getAddress());
		callback.mergeLocalProperties(p);
		// Bind a server socket to accept connections from contacts
		BluetoothServerSocket ss = null;
		try {
			ss = InsecureBluetooth.listen(adapter, "RFCOMM", getUuid());
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(ss);
			return;
		}
		if(!running) {
			tryToClose(ss);
			return;
		}
		acceptContactConnections(ss);
	}

	private boolean enableBluetooth() {
		isEnabled = adapter.isEnabled();
		if(isEnabled) return true;
		// Try to enable the adapter and wait for the result
		if(LOG.isLoggable(INFO)) LOG.info("Enabling Bluetooth");
		IntentFilter filter = new IntentFilter(ACTION_STATE_CHANGED);
		BluetoothStateReceiver receiver = new BluetoothStateReceiver();
		appContext.registerReceiver(receiver, filter);
		try {
			if(adapter.enable()) {
				isEnabled = receiver.waitForStateChange();
				if(LOG.isLoggable(INFO)) LOG.info("Enabled: " + isEnabled);
				return isEnabled;
			} else {
				if(LOG.isLoggable(INFO)) LOG.info("Could not enable adapter");
				return false;
			}
		} catch(InterruptedException e) {
			if(LOG.isLoggable(INFO))
				LOG.info("Interrupted while enabling Bluetooth");
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private UUID getUuid() {
		String uuid = callback.getLocalProperties().get("uuid");
		if(uuid == null) {
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
			if(ss != null) ss.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void acceptContactConnections(BluetoothServerSocket ss) {
		while(true) {
			BluetoothSocket s;
			try {
				s = ss.accept();
			} catch(IOException e) {
				// This is expected when the socket is closed
				if(LOG.isLoggable(INFO)) LOG.log(INFO, e.toString(), e);
				tryToClose(ss);
				return;
			}
			callback.incomingConnectionCreated(wrapSocket(s));
			if(!running) return;
		}
	}

	private DuplexTransportConnection wrapSocket(BluetoothSocket s) {
		return new DroidtoothTransportConnection(this, s);
	}

	public void stop() {
		running = false;
		// Disable Bluetooth if we enabled it at startup
		if(isEnabled && !wasEnabled) disableBluetooth();
	}

	private void disableBluetooth() {
		isEnabled = adapter.isEnabled();
		if(!isEnabled) return;
		// Try to disable the adapter and wait for the result
		if(LOG.isLoggable(INFO)) LOG.info("Disabling Bluetooth");
		IntentFilter filter = new IntentFilter(ACTION_STATE_CHANGED);
		BluetoothStateReceiver receiver = new BluetoothStateReceiver();
		appContext.registerReceiver(receiver, filter);
		try {
			if(adapter.disable()) {
				isEnabled = receiver.waitForStateChange();
				if(LOG.isLoggable(INFO)) LOG.info("Enabled: " + isEnabled);
			} else {
				if(LOG.isLoggable(INFO)) LOG.info("Could not disable adapter");
			}
		} catch(InterruptedException e) {
			if(LOG.isLoggable(INFO))
				LOG.info("Interrupted while disabling Bluetooth");
			Thread.currentThread().interrupt();
		}
	}

	public boolean shouldPoll() {
		return true;
	}

	public long getPollingInterval() {
		return pollingInterval;
	}

	public void poll(Collection<ContactId> connected) {
		if(!running) return;
		if(!enableBluetooth()) return;
		// Try to connect to known devices in parallel
		Map<ContactId, TransportProperties> remote =
				callback.getRemoteProperties();
		for(Entry<ContactId, TransportProperties> e : remote.entrySet()) {
			final ContactId c = e.getKey();
			if(connected.contains(c)) continue;
			final String address = e.getValue().get("address");
			if(StringUtils.isNullOrEmpty(address)) continue;
			final String uuid = e.getValue().get("uuid");
			if(StringUtils.isNullOrEmpty(uuid)) continue;
			pluginExecutor.execute(new Runnable() {
				public void run() {
					if(!running) return;
					BluetoothSocket s = connect(address, uuid);
					if(s != null)
						callback.outgoingConnectionCreated(c, wrapSocket(s));
				}
			});
		}
	}

	private BluetoothSocket connect(String address, String uuid) {
		// Validate the address
		if(!BluetoothAdapter.checkBluetoothAddress(address)) {
			if(LOG.isLoggable(WARNING))
				LOG.warning("Invalid address " + address);
			return null;
		}
		// Validate the UUID
		UUID u;
		try {
			u = UUID.fromString(uuid);
		} catch(IllegalArgumentException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning("Invalid UUID " + uuid);
			return null;
		}
		// Try to connect
		BluetoothDevice d = adapter.getRemoteDevice(address);
		BluetoothSocket s = null;
		try {
			s = InsecureBluetooth.createSocket(d, u);
			if(LOG.isLoggable(INFO)) LOG.info("Connecting to " + address);
			s.connect();
			if(LOG.isLoggable(INFO)) LOG.info("Connected to " + address);
			return s;
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(s);
			return null;
		}
	}

	private void tryToClose(BluetoothSocket s) {
		try {
			if(s != null) s.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	public DuplexTransportConnection createConnection(ContactId c) {
		if(!running) return null;
		TransportProperties p = callback.getRemoteProperties().get(c);
		if(p == null) return null;
		String address = p.get("address");
		if(StringUtils.isNullOrEmpty(address)) return null;
		String uuid = p.get("uuid");
		if(StringUtils.isNullOrEmpty(uuid)) return null;
		BluetoothSocket s = connect(address, uuid);
		if(s == null) return null;
		return new DroidtoothTransportConnection(this, s);
	}

	public boolean supportsInvitations() {
		return true;
	}

	public DuplexTransportConnection createInvitationConnection(PseudoRandom r,
			long timeout) {
		if(!running) return null;
		// Use the invitation codes to generate the UUID
		byte[] b = r.nextBytes(UUID_BYTES);
		UUID uuid = UUID.nameUUIDFromBytes(b);
		if(LOG.isLoggable(INFO)) LOG.info("Invitation UUID " + uuid);
		// Bind a server socket for receiving invitation connections
		BluetoothServerSocket ss = null;
		try {
			ss = InsecureBluetooth.listen(adapter, "RFCOMM", uuid);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(ss);
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
			if(s != null) return new DroidtoothTransportConnection(this, s);
		} catch(InterruptedException e) {
			if(LOG.isLoggable(INFO))
				LOG.info("Interrupted while exchanging invitations");
			Thread.currentThread().interrupt();
		} finally {
			// Closing the socket will terminate the listener thread
			tryToClose(ss);
		}
		return null;
	}

	private static class BluetoothStateReceiver extends BroadcastReceiver {

		private final CountDownLatch finished = new CountDownLatch(1);

		private volatile boolean enabled = false;

		public void onReceive(Context ctx, Intent intent) {
			int state = intent.getIntExtra(EXTRA_STATE, 0);
			if(state == STATE_ON) {
				enabled = true;
				ctx.unregisterReceiver(this);
				finished.countDown();
			} else if(state == STATE_OFF) {
				ctx.unregisterReceiver(this);
				finished.countDown();
			}
		}

		boolean waitForStateChange() throws InterruptedException {
			finished.await();
			return enabled;
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
			long now = clock.currentTimeMillis();
			long end = now + timeout;
			while(now < end && running && !socketLatch.isSet()) {
				// Discover nearby devices
				if(LOG.isLoggable(INFO)) LOG.info("Discovering nearby devices");
				List<String> addresses;
				try {
					addresses = discoverDevices(end - now);
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while discovering devices");
					return;
				}
				// Connect to any device with the right UUID
				for(String address : addresses) {
					now = clock.currentTimeMillis();
					if(now < end  && running && !socketLatch.isSet()) {
						BluetoothSocket s = connect(address, uuid);
						if(s == null) continue;
						if(LOG.isLoggable(INFO))
							LOG.info("Outgoing connection");
						if(!socketLatch.set(s)) {
							if(LOG.isLoggable(INFO))
								LOG.info("Closing redundant connection");
							tryToClose(s);
						}
						return;
					}
				}
			}
		}

		private List<String> discoverDevices(long timeout)
				throws InterruptedException {
			IntentFilter filter = new IntentFilter();
			filter.addAction(FOUND);
			filter.addAction(DISCOVERY_FINISHED);
			DiscoveryReceiver disco = new DiscoveryReceiver();
			appContext.registerReceiver(disco, filter);
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
			if(action.equals(DISCOVERY_FINISHED)) {
				ctx.unregisterReceiver(this);
				finished.countDown();
			} else if(action.equals(FOUND)) {
				BluetoothDevice d = intent.getParcelableExtra(EXTRA_DEVICE);
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
				if(LOG.isLoggable(INFO)) LOG.info("Incoming connection");
				if(!socketLatch.set(s)) {
					if(LOG.isLoggable(INFO))
						LOG.info("Closing redundant connection");
					s.close();
				}
			} catch(IOException e) {
				// This is expected when the socket is closed
				if(LOG.isLoggable(INFO)) LOG.log(INFO, e.toString(), e);
			}
		}
	}
}
