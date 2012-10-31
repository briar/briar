package net.sf.briar.plugins.droidtooth;

import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED;
import static android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION;
import static android.bluetooth.BluetoothAdapter.EXTRA_STATE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.bluetooth.BluetoothAdapter.STATE_OFF;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static android.bluetooth.BluetoothDevice.EXTRA_DEVICE;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.util.StringUtils;
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
	public static final byte[] TRANSPORT_ID =
			StringUtils.fromHexString("d99c9313c04417dcf22fc60d12a187ea"
					+ "00a539fd260f08a13a0d8a900cde5e49"
					+ "1b4df2ffd42e40c408f2db7868f518aa");

	private static final TransportId ID = new TransportId(TRANSPORT_ID);
	private static final Logger LOG =
			Logger.getLogger(DroidtoothPlugin.class.getName());
	private static final String FOUND = "android.bluetooth.device.action.FOUND";
	private static final String DISCOVERY_FINISHED =
			"android.bluetooth.adapter.action.DISCOVERY_FINISHED";

	private final Executor pluginExecutor;
	private final AndroidExecutor androidExecutor;
	private final Context appContext;
	private final DuplexPluginCallback callback;
	private final long pollingInterval;

	private boolean running = false; // Locking: this
	private BluetoothServerSocket socket = null; // Locking: this

	// Non-null if running has ever been true
	private volatile BluetoothAdapter adapter = null;

	DroidtoothPlugin(@PluginExecutor Executor pluginExecutor,
			AndroidExecutor androidExecutor, Context appContext,
			DuplexPluginCallback callback, long pollingInterval) {
		this.pluginExecutor = pluginExecutor;
		this.androidExecutor = androidExecutor;
		this.appContext = appContext;
		this.callback = callback;
		this.pollingInterval = pollingInterval;
	}

	public TransportId getId() {
		return ID;
	}

	public void start() throws IOException {
		// BluetoothAdapter.getDefaultAdapter() must be called on a thread
		// with a message queue, so submit it to the AndroidExecutor
		Callable<BluetoothAdapter> c = new Callable<BluetoothAdapter>() {
			public BluetoothAdapter call() throws Exception {
				return BluetoothAdapter.getDefaultAdapter();
			}			
		};
		Future<BluetoothAdapter> f = androidExecutor.submit(c);
		try {
			adapter = f.get();
		} catch(InterruptedException e) {
			throw new IOException(e.toString());
		} catch(ExecutionException e) {
			throw new IOException(e.toString());
		}
		if(adapter == null) throw new IOException(); // Bluetooth not supported
		synchronized(this) {
			running = true;
		}
		pluginExecutor.execute(new Runnable() {
			public void run() {
				bind();
			}
		});
	}

	private void bind() {
		synchronized(this) {
			if(!running) return;
		}
		if(!enableBluetooth()) {
			if(LOG.isLoggable(INFO)) LOG.info("Could not enable Bluetooth");
			return;
		}
		if(LOG.isLoggable(INFO))
			LOG.info("Local address " + adapter.getAddress());
		// Advertise the Bluetooth address to contacts
		TransportProperties p = new TransportProperties();
		p.put("address", adapter.getAddress());
		callback.mergeLocalProperties(p);
		// Bind a server socket to accept connections from contacts
		BluetoothServerSocket ss;
		try {
			ss = InsecureBluetooth.listen(adapter, "RFCOMM", getUuid(), false);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			return;
		}
		synchronized(this) {
			if(!running) {
				tryToClose(ss);
				return;
			}
			socket = ss;
		}
		acceptContactConnections(ss);
	}

	private boolean enableBluetooth() {
		synchronized(this) {
			if(!running) return false;
		}
		if(adapter.isEnabled()) return true;
		// Try to enable the adapter and wait for the result
		IntentFilter filter = new IntentFilter(ACTION_STATE_CHANGED);
		BluetoothStateReceiver receiver = new BluetoothStateReceiver();
		appContext.registerReceiver(receiver, filter);
		try {
			if(!adapter.enable()) return false;
			return receiver.waitForStateChange();
		} catch(InterruptedException e) {
			if(LOG.isLoggable(INFO))
				LOG.info("Interrupted while enabling Bluetooth");
			Thread.currentThread().interrupt();
			return false;
		}
	}

	// FIXME: Get the UUID from the local transport properties
	private UUID getUuid() {
		return UUID.nameUUIDFromBytes(new byte[0]);
	}

	private void tryToClose(BluetoothServerSocket ss) {
		try {
			ss.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		}
	}

	private void acceptContactConnections(BluetoothServerSocket ss) {
		while(true) {
			BluetoothSocket s;
			try {
				s = ss.accept();
			} catch(IOException e) {
				// This is expected when the socket is closed
				if(LOG.isLoggable(INFO)) LOG.info(e.toString());
				tryToClose(ss);
				return;
			}
			DroidtoothTransportConnection conn =
					new DroidtoothTransportConnection(s);
			callback.incomingConnectionCreated(conn);
			synchronized(this) {
				if(!running) return;
			}
		}
	}

	public void stop() throws IOException {
		synchronized(this) {
			running = false;
			if(socket != null) {
				tryToClose(socket);
				socket = null;
			}
		}
	}

	public boolean shouldPoll() {
		return true;
	}

	public long getPollingInterval() {
		return pollingInterval;
	}

	public void poll(Collection<ContactId> connected) {
		synchronized(this) {
			if(!running) return;
		}
		// Try to connect to known devices in parallel
		Map<ContactId, TransportProperties> remote =
				callback.getRemoteProperties();
		for(Entry<ContactId, TransportProperties> e : remote.entrySet()) {
			final ContactId c = e.getKey();
			if(connected.contains(c)) continue;
			final String address = e.getValue().get("address");
			final String uuid = e.getValue().get("uuid");
			if(address != null && uuid != null) {
				pluginExecutor.execute(new Runnable() {
					public void run() {
						synchronized(DroidtoothPlugin.this) {
							if(!running) return;
						}
						DuplexTransportConnection conn = connect(address, uuid);
						if(conn != null)
							callback.outgoingConnectionCreated(c, conn);
					}
				});
			}
		}
	}

	private DuplexTransportConnection connect(String address, String uuid) {
		// Validate the address
		if(!BluetoothAdapter.checkBluetoothAddress(address)) {
			if(LOG.isLoggable(WARNING))
				LOG.warning("Invalid address " + address);
			return null;
		}
		BluetoothDevice d = adapter.getRemoteDevice(address);
		// Validate the UUID
		UUID u;
		try {
			u = UUID.fromString(uuid);
		} catch(IllegalArgumentException e) {
			if(LOG.isLoggable(WARNING))
				LOG.warning("Invalid UUID " + uuid);
			return null;
		}
		// Try to connect
		try {
			BluetoothSocket s = InsecureBluetooth.createSocket(d, u, false);
			return new DroidtoothTransportConnection(s);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			return null;
		}
	}

	public DuplexTransportConnection createConnection(ContactId c) {
		synchronized(this) {
			if(!running) return null;
		}
		TransportProperties p = callback.getRemoteProperties().get(c);
		if(p == null) return null;
		String address = p.get("address");
		String uuid = p.get("uuid");
		if(address == null || uuid == null) return null;
		return connect(address, uuid);
	}

	public boolean supportsInvitations() {
		return true;
	}

	public DuplexTransportConnection sendInvitation(PseudoRandom r,
			long timeout) {
		synchronized(this) {
			if(!running) return null;
		}
		// Use the same pseudo-random UUID as the contact
		String uuid = UUID.nameUUIDFromBytes(r.nextBytes(16)).toString();
		// Register to receive Bluetooth discovery intents
		IntentFilter filter = new IntentFilter();
		filter.addAction(FOUND);
		filter.addAction(DISCOVERY_FINISHED);
		// Discover nearby devices and connect to any with the right UUID
		DiscoveryReceiver receiver = new DiscoveryReceiver(uuid);
		appContext.registerReceiver(receiver, filter);
		adapter.startDiscovery();
		try {
			return receiver.waitForConnection(timeout);
		} catch(InterruptedException e) {
			if(LOG.isLoggable(INFO))
				LOG.info("Interrupted while sending invitation");
			Thread.currentThread().interrupt();
			return null;
		}
	}

	public DuplexTransportConnection acceptInvitation(PseudoRandom r,
			long timeout) {
		synchronized(this) {
			if(!running) return null;
		}
		// Use the same pseudo-random UUID as the contact
		UUID uuid = UUID.nameUUIDFromBytes(r.nextBytes(16));
		// Make the device discoverable if the user allows it
		makeDeviceDiscoverable();
		// Bind a new server socket to accept the invitation connection
		final BluetoothServerSocket ss;
		try {
			ss = InsecureBluetooth.listen(adapter, "RFCOMM", uuid, false);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			return null;
		}
		// Return the first connection received by the socket, if any
		try {
			return new DroidtoothTransportConnection(ss.accept((int) timeout));
		} catch(SocketTimeoutException e) {
			if(LOG.isLoggable(INFO)) LOG.info("Invitation timed out");
			return null;
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			return null;
		} finally {
			tryToClose(ss);
		}
	}

	private void makeDeviceDiscoverable() {
		synchronized(this) {
			if(!running) return;
		}
		if(adapter.getScanMode() == SCAN_MODE_CONNECTABLE_DISCOVERABLE) return;
		Intent intent = new Intent(ACTION_REQUEST_DISCOVERABLE);
		intent.putExtra(EXTRA_DISCOVERABLE_DURATION, 60);
		intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
		appContext.startActivity(intent);
	}

	private static class BluetoothStateReceiver extends BroadcastReceiver {

		private final CountDownLatch finished = new CountDownLatch(1);

		private volatile boolean enabled = false;

		@Override
		public void onReceive(Context ctx, Intent intent) {
			int state = intent.getIntExtra(EXTRA_STATE, 0);
			if(state == STATE_ON) {
				enabled = true;
				finish(ctx);
			} else if(state == STATE_OFF) {
				finish(ctx);
			}
		}

		private void finish(Context ctx) {
			ctx.unregisterReceiver(this);
			finished.countDown();
		}

		boolean waitForStateChange() throws InterruptedException {
			finished.await();
			return enabled;
		}
	}

	private class DiscoveryReceiver extends BroadcastReceiver {

		private final CountDownLatch finished = new CountDownLatch(1);
		private final String uuid;

		private volatile DuplexTransportConnection connection = null;

		private DiscoveryReceiver(String uuid) {
			this.uuid = uuid;
		}

		@Override
		public void onReceive(final Context ctx, Intent intent) {
			String action = intent.getAction();
			if(action.equals(DISCOVERY_FINISHED)) {
				finish(ctx);
			} else if(action.equals(FOUND)) {
				BluetoothDevice d = intent.getParcelableExtra(EXTRA_DEVICE);
				final String address = d.getAddress();
				pluginExecutor.execute(new Runnable() {
					public void run() {
						synchronized(DroidtoothPlugin.this) {
							if(!running) return;
						}
						DuplexTransportConnection conn = connect(address, uuid);
						if(conn != null) {
							connection = conn;
							finish(ctx);
						}
					}
				});
			}
		}

		private void finish(Context ctx) {
			ctx.unregisterReceiver(this);
			finished.countDown();
		}

		private DuplexTransportConnection waitForConnection(long timeout)
				throws InterruptedException {
			finished.await(timeout, MILLISECONDS);
			return connection;
		}
	}
}
