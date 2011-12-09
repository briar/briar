package net.sf.briar.plugins.bluetooth;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.PluginExecutor;
import net.sf.briar.api.plugins.StreamPlugin;
import net.sf.briar.api.plugins.StreamPluginCallback;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.transport.StreamTransportConnection;
import net.sf.briar.util.OsUtils;
import net.sf.briar.util.StringUtils;

class BluetoothPlugin implements StreamPlugin {

	public static final byte[] TRANSPORT_ID =
		StringUtils.fromHexString("d99c9313c04417dcf22fc60d12a187ea"
				+ "00a539fd260f08a13a0d8a900cde5e49");

	private static final TransportId id = new TransportId(TRANSPORT_ID);
	private static final Logger LOG =
		Logger.getLogger(BluetoothPlugin.class.getName());

	private final Executor pluginExecutor;
	private final StreamPluginCallback callback;
	private final long pollingInterval;
	private final Object discoveryLock = new Object();

	private boolean running = false; // Locking: this
	private LocalDevice localDevice = null; // Locking: this
	private StreamConnectionNotifier socket = null; // Locking: this

	BluetoothPlugin(@PluginExecutor Executor pluginExecutor,
			StreamPluginCallback callback, long pollingInterval) {
		this.pluginExecutor = pluginExecutor;
		this.callback = callback;
		this.pollingInterval = pollingInterval;
	}

	public TransportId getId() {
		return id;
	}

	public void start() throws IOException {
		// Initialise the Bluetooth stack
		try {
			synchronized(this) {
				running = true;
				localDevice = LocalDevice.getLocalDevice();
			} 
		} catch(UnsatisfiedLinkError e) {
			// On Linux the user may need to install libbluetooth-dev
			if(OsUtils.isLinux())
				callback.showMessage("BLUETOOTH_INSTALL_LIBS");
			throw new IOException(e.toString());
		}
		pluginExecutor.execute(new Runnable() {
			public void run() {
				bind();
			}
		});
	}

	private void bind() {
		String uuid;
		synchronized(this) {
			if(!running) return;
			uuid = getUuid();
			makeDeviceDiscoverable();
		}
		String url = "btspp://localhost:" + uuid + ";name=RFCOMM";
		StreamConnectionNotifier scn;
		try {
			scn = (StreamConnectionNotifier) Connector.open(url);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
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

	private synchronized String getUuid() {
		assert running;
		TransportProperties p = callback.getLocalProperties();
		String uuid = p.get("uuid");
		if(uuid == null) {
			// Generate a (weakly) random UUID and store it
			byte[] b = new byte[16];
			new Random().nextBytes(b);
			uuid = StringUtils.toHexString(b);
			p.put("uuid", uuid);
			callback.setLocalProperties(p);
		}
		return uuid;
	}

	private synchronized void makeDeviceDiscoverable() {
		assert running;
		// Try to make the device discoverable (requires root on Linux)
		try {
			localDevice.setDiscoverable(DiscoveryAgent.GIAC);
		} catch(BluetoothStateException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
		}
		// Advertise the address to contacts if the device is discoverable
		if(localDevice.getDiscoverable() != DiscoveryAgent.NOT_DISCOVERABLE) {
			TransportProperties p = callback.getLocalProperties();
			p.put("address", localDevice.getBluetoothAddress());
			callback.setLocalProperties(p);
		}
	}

	private void tryToClose(StreamConnectionNotifier scn) {
		try {
			scn.close();
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
		}
	}

	private void acceptContactConnections(StreamConnectionNotifier scn) {
		while(true) {
			StreamConnection s;
			try {
				s = scn.acceptAndOpen();
			} catch(IOException e) {
				// This is expected when the socket is closed
				if(LOG.isLoggable(Level.INFO)) LOG.info(e.toString());
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

	public synchronized void stop() throws IOException {
		running = false;
		localDevice = null;
		if(socket != null) {
			socket.close();
			socket = null;
		}
	}

	public boolean shouldPoll() {
		return true;
	}

	public long getPollingInterval() {
		return pollingInterval;
	}

	public void poll() {
		synchronized(this) {
			if(!running) return;
		}
		pluginExecutor.execute(new Runnable() {
			public void run() {
				connectAndCallBack();
			}
		});
	}

	private void connectAndCallBack() {
		synchronized(this) {
			if(!running) return;
		}
		Map<ContactId, TransportProperties> remote =
			callback.getRemoteProperties();
		Map<ContactId, String> discovered = discoverContactUrls(remote);
		for(Entry<ContactId, String> e : discovered.entrySet()) {
			ContactId c = e.getKey();
			String url = e.getValue();
			StreamTransportConnection s = connect(c, url);
			if(s != null) callback.outgoingConnectionCreated(c, s);
		}
	}

	private Map<ContactId, String> discoverContactUrls(
			Map<ContactId, TransportProperties> remote) {
		DiscoveryAgent discoveryAgent;
		synchronized(this) {
			if(!running) return Collections.emptyMap();
			discoveryAgent = localDevice.getDiscoveryAgent();
		}
		Map<String, ContactId> addresses = new HashMap<String, ContactId>();
		Map<ContactId, String> uuids = new HashMap<ContactId, String>();
		for(Entry<ContactId, TransportProperties> e : remote.entrySet()) {
			ContactId c = e.getKey();
			TransportProperties p = e.getValue();
			String address = p.get("address");
			String uuid = p.get("uuid");
			if(address != null && uuid != null) {
				addresses.put(address, c);
				uuids.put(c, uuid);
			}
		}
		if(addresses.isEmpty()) return Collections.emptyMap();
		ContactListener listener = new ContactListener(discoveryAgent,
				Collections.unmodifiableMap(addresses),
				Collections.unmodifiableMap(uuids));
		synchronized(discoveryLock) {
			try {
				discoveryAgent.startInquiry(DiscoveryAgent.GIAC, listener);
				return listener.waitForUrls();
			} catch(BluetoothStateException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
				return Collections.emptyMap();
			} catch(InterruptedException e) {
				if(LOG.isLoggable(Level.INFO))
					LOG.info("Interrupted while waiting for URLs");
				Thread.currentThread().interrupt();
				return Collections.emptyMap();
			}
		}
	}

	private StreamTransportConnection connect(ContactId c, String url) {
		synchronized(this) {
			if(!running) return null;
		}
		try {
			StreamConnection s = (StreamConnection) Connector.open(url);
			return new BluetoothTransportConnection(s);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			return null;
		}
	}

	public StreamTransportConnection createConnection(ContactId c) {
		synchronized(this) {
			if(!running) return null;
		}
		Map<ContactId, TransportProperties> remote =
			callback.getRemoteProperties();
		if(!remote.containsKey(c)) return null;
		remote = Collections.singletonMap(c, remote.get(c));
		String url = discoverContactUrls(remote).get(c);
		return url == null ? null : connect(c, url);
	}

	public boolean supportsInvitations() {
		return true;
	}

	public StreamTransportConnection sendInvitation(int code, long timeout) {
		return createInvitationConnection(code, timeout);
	}

	public StreamTransportConnection acceptInvitation(int code, long timeout) {
		return createInvitationConnection(code, timeout);
	}

	private StreamTransportConnection createInvitationConnection(int code,
			long timeout) {
		synchronized(this) {
			if(!running) return null;
		}
		// The invitee's device may not be discoverable, so both parties must
		// try to initiate connections
		String uuid = convertInvitationCodeToUuid(code);
		final ConnectionCallback c = new ConnectionCallback(uuid, timeout);
		pluginExecutor.execute(new Runnable() {
			public void run() {
				createInvitationConnection(c);
			}
		});
		pluginExecutor.execute(new Runnable() {
			public void run() {
				bindInvitationSocket(c);
			}
		});
		try {
			StreamConnection s = c.waitForConnection();
			return s == null ? null : new BluetoothTransportConnection(s);
		} catch(InterruptedException e) {
			if(LOG.isLoggable(Level.INFO))
				LOG.info("Interrupted while waiting for connection");
			Thread.currentThread().interrupt();
			return null;
		}
	}

	private String convertInvitationCodeToUuid(int code) {
		byte[] b = new byte[16];
		new Random(code).nextBytes(b);
		return StringUtils.toHexString(b);
	}

	private void createInvitationConnection(ConnectionCallback c) {
		DiscoveryAgent discoveryAgent;
		synchronized(this) {
			if(!running) return;
			discoveryAgent = localDevice.getDiscoveryAgent();
		}
		// Try to discover the other party until the invitation times out
		long end = System.currentTimeMillis() + c.getTimeout();
		String url = null;
		while(url == null && System.currentTimeMillis() < end) {
			InvitationListener listener = new InvitationListener(discoveryAgent,
					c.getUuid());
			synchronized(discoveryLock) {
				try {
					discoveryAgent.startInquiry(DiscoveryAgent.GIAC, listener);
					url = listener.waitForUrl();
				} catch(BluetoothStateException e) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning(e.toString());
					return;
				} catch(InterruptedException e) {
					if(LOG.isLoggable(Level.INFO))
						LOG.info("Interrupted while waiting for URL");
					Thread.currentThread().interrupt();
					return;
				}
			}
			synchronized(this) {
				if(!running) return;
			}
		}
		if(url == null) return;
		// Try to connect to the other party
		try {
			StreamConnection s = (StreamConnection) Connector.open(url);
			c.addConnection(s);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
		}
	}

	private void bindInvitationSocket(final ConnectionCallback c) {
		synchronized(this) {
			if(!running) return;
			makeDeviceDiscoverable();
		}
		String url = "btspp://localhost:" + c.getUuid() + ";name=RFCOMM";
		final StreamConnectionNotifier scn;
		try {
			scn = (StreamConnectionNotifier) Connector.open(url);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
			return;
		}
		// Close the socket when the invitation times out
		pluginExecutor.execute(new Runnable() {
			public void run() {
				try {
					Thread.sleep(c.getTimeout());
				} catch(InterruptedException e) {
					if(LOG.isLoggable(Level.INFO))
						LOG.info("Interrupted while waiting for invitation");
					Thread.currentThread().interrupt();
				}
				tryToClose(scn);
			}
		});
		try {
			StreamConnection s = scn.acceptAndOpen();
			c.addConnection(s);
		} catch(IOException e) {
			// This is expected when the socket is closed
			if(LOG.isLoggable(Level.INFO)) LOG.info(e.toString());
			tryToClose(scn);
		}
	}
}
