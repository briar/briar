package net.sf.briar.plugins.bluetooth;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static javax.bluetooth.DiscoveryAgent.GIAC;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.util.OsUtils;
import net.sf.briar.util.StringUtils;

class BluetoothPlugin implements DuplexPlugin {

	// Share an ID with the Android Bluetooth plugin
	private static final byte[] TRANSPORT_ID =
			StringUtils.fromHexString("d99c9313c04417dcf22fc60d12a187ea"
					+ "00a539fd260f08a13a0d8a900cde5e49"
					+ "1b4df2ffd42e40c408f2db7868f518aa");
	private static final TransportId ID = new TransportId(TRANSPORT_ID);
	private static final Logger LOG =
			Logger.getLogger(BluetoothPlugin.class.getName());

	private final Executor pluginExecutor;
	private final Clock clock;
	private final DuplexPluginCallback callback;
	private final long pollingInterval;
	private final Object discoveryLock = new Object();
	private final ScheduledExecutorService scheduler;

	private boolean running = false; // Locking: this
	private StreamConnectionNotifier socket = null; // Locking: this

	// Non-null if running has ever been true
	private volatile LocalDevice localDevice = null;

	BluetoothPlugin(@PluginExecutor Executor pluginExecutor, Clock clock,
			DuplexPluginCallback callback, long pollingInterval) {
		this.pluginExecutor = pluginExecutor;
		this.clock = clock;
		this.callback = callback;
		this.pollingInterval = pollingInterval;
		scheduler = Executors.newScheduledThreadPool(0);
	}

	public TransportId getId() {
		return ID;
	}

	public String getName() {
		return "BLUETOOTH_PLUGIN_NAME";
	}

	public void start() throws IOException {
		// Initialise the Bluetooth stack
		try {
			localDevice = LocalDevice.getLocalDevice();
		} catch(UnsatisfiedLinkError e) {
			// On Linux the user may need to install libbluetooth-dev
			if(OsUtils.isLinux())
				callback.showMessage("BLUETOOTH_INSTALL_LIBS");
			throw new IOException(e.toString());
		}
		if(LOG.isLoggable(INFO))
			LOG.info("Local address " + localDevice.getBluetoothAddress());
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
		// Advertise the Bluetooth address to contacts
		TransportProperties p = new TransportProperties();
		p.put("address", localDevice.getBluetoothAddress());
		callback.mergeLocalProperties(p);
		String url = makeUrl("localhost", getUuid());
		StreamConnectionNotifier scn;
		try {
			scn = (StreamConnectionNotifier) Connector.open(url);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			return;
		}
		synchronized(this) {
			if(!running) {
				tryToClose(scn);
				return;
			}
			socket = scn;
		}
		acceptContactConnections(scn);
	}

	private String makeUrl(String address, String uuid) {
		return "btspp://" + address + ":" + uuid + ";name=RFCOMM";
	}

	// FIXME: Get the UUID from the local transport properties
	private String getUuid() {
		return UUID.nameUUIDFromBytes(new byte[0]).toString();
	}

	private void tryToClose(StreamConnectionNotifier scn) {
		try {
			scn.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		}
	}

	private void acceptContactConnections(StreamConnectionNotifier scn) {
		while(true) {
			StreamConnection s;
			try {
				s = scn.acceptAndOpen();
			} catch(IOException e) {
				// This is expected when the socket is closed
				if(LOG.isLoggable(INFO)) LOG.info(e.toString());
				tryToClose(scn);
				return;
			}
			BluetoothTransportConnection conn =
					new BluetoothTransportConnection(s);
			callback.incomingConnectionCreated(conn);
			synchronized(this) {
				if(!running) return;
			}
		}
	}

	public void stop() {
		synchronized(this) {
			running = false;
			if(socket != null) {
				tryToClose(socket);
				socket = null;
			}
		}
		scheduler.shutdownNow();
	}

	public boolean shouldPoll() {
		return true;
	}

	public long getPollingInterval() {
		return pollingInterval;
	}

	public void poll(final Collection<ContactId> connected) {
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
						synchronized(BluetoothPlugin.this) {
							if(!running) return;
						}
						String url = makeUrl(address, uuid);
						DuplexTransportConnection conn = connect(url);
						if(conn != null)
							callback.outgoingConnectionCreated(c, conn);
					}
				});
			}
		}
	}

	private DuplexTransportConnection connect(String url) {
		try {
			StreamConnection s = (StreamConnection) Connector.open(url);
			return new BluetoothTransportConnection(s);
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
		String url = makeUrl(address, uuid);
		return connect(url);
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
		String uuid = generateUuid(r.nextBytes(16));
		// Discover nearby devices and connect to any with the right UUID
		DiscoveryAgent discoveryAgent = localDevice.getDiscoveryAgent();
		long end = clock.currentTimeMillis() + timeout;
		String url = null;
		while(url == null && clock.currentTimeMillis() < end) {
			InvitationListener listener =
					new InvitationListener(discoveryAgent, uuid);
			// FIXME: Avoid making alien calls with a lock held
			synchronized(discoveryLock) {
				try {
					discoveryAgent.startInquiry(GIAC, listener);
					url = listener.waitForUrl();
				} catch(BluetoothStateException e) {
					if(LOG.isLoggable(WARNING))
						LOG.warning(e.toString());
					return null;
				} catch(InterruptedException e) {
					if(LOG.isLoggable(INFO))
						LOG.info("Interrupted while waiting for URL");
					Thread.currentThread().interrupt();
					return null;
				}
			}
			synchronized(this) {
				if(!running) return null;
			}
		}
		if(url == null) return null;
		return connect(url);
	}

	private String generateUuid(byte[] b) {
		UUID uuid = UUID.nameUUIDFromBytes(b);
		return uuid.toString().replaceAll("-", "");
	}

	public DuplexTransportConnection acceptInvitation(PseudoRandom r,
			long timeout) {
		synchronized(this) {
			if(!running) return null;
		}
		// Use the same pseudo-random UUID as the contact
		String uuid = generateUuid(r.nextBytes(16));
		String url = makeUrl("localhost", uuid);
		// Make the device discoverable if possible
		makeDeviceDiscoverable();
		// Bind a socket for accepting the invitation connection
		final StreamConnectionNotifier scn;
		try {
			scn = (StreamConnectionNotifier) Connector.open(url);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
			return null;
		}
		synchronized(this) {
			if(!running) {
				tryToClose(scn);
				return null;
			}
		}
		// Close the socket when the invitation times out
		Runnable close = new Runnable() {
			public void run() {
				tryToClose(scn);
			}
		};
		ScheduledFuture<?> f = scheduler.schedule(close, timeout, MILLISECONDS);
		// Try to accept a connection and close the socket
		try {
			StreamConnection s = scn.acceptAndOpen();
			return new BluetoothTransportConnection(s);
		} catch(IOException e) {
			// This is expected when the socket is closed
			if(LOG.isLoggable(INFO)) LOG.info(e.toString());
			return null;
		} finally {
			if(f.cancel(false)) tryToClose(scn);
		}
	}

	private void makeDeviceDiscoverable() {
		// Try to make the device discoverable (requires root on Linux)
		synchronized(this) {
			if(!running) return;
		}
		try {
			localDevice.setDiscoverable(GIAC);
		} catch(BluetoothStateException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		}
	}
}
