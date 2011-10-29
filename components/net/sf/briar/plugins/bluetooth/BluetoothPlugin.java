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
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.StreamPlugin;
import net.sf.briar.api.plugins.StreamPluginCallback;
import net.sf.briar.api.transport.StreamTransportConnection;
import net.sf.briar.plugins.AbstractPlugin;
import net.sf.briar.util.OsUtils;
import net.sf.briar.util.StringUtils;

class BluetoothPlugin extends AbstractPlugin implements StreamPlugin {

	public static final int TRANSPORT_ID = 2;

	private static final TransportId id = new TransportId(TRANSPORT_ID);
	private static final Logger LOG =
		Logger.getLogger(BluetoothPlugin.class.getName());

	private final Object discoveryLock = new Object();
	private final StreamPluginCallback callback;
	private final long pollingInterval;

	private LocalDevice localDevice = null; // Locking: this
	private StreamConnectionNotifier socket = null; // Locking: this

	BluetoothPlugin(Executor executor, StreamPluginCallback callback,
			long pollingInterval) {
		super(executor);
		this.callback = callback;
		this.pollingInterval = pollingInterval;
	}

	public TransportId getId() {
		return id;
	}

	@Override
	public void start() throws IOException {
		// Initialise the Bluetooth stack
		try {
			synchronized(this) {
				super.start();
				localDevice = LocalDevice.getLocalDevice();
			}
		} catch(UnsatisfiedLinkError e) {
			// On Linux the user may need to install libbluetooth-dev
			if(OsUtils.isLinux()) {
				executor.execute(new Runnable() {
					public void run() {
						callback.showMessage("BLUETOOTH_INSTALL_LIBS");
					}
				});
			}
			throw new IOException(e.getMessage());
		}
		executor.execute(createContactSocketBinder());
	}

	@Override
	public synchronized void stop() throws IOException {
		super.stop();
		if(socket != null) {
			socket.close();
			socket = null;
		}
	}

	private Runnable createContactSocketBinder() {
		return new Runnable() {
			public void run() {
				bindContactSocket();
			}
		};
	}

	private void bindContactSocket() {
		String uuid;
		synchronized(this) {
			if(!started) return;
			uuid = getUuid();
			makeDeviceDiscoverable();
		}
		// Bind the socket
		String url = "btspp://localhost:" + uuid + ";name=RFCOMM";
		StreamConnectionNotifier scn;
		try {
			scn = (StreamConnectionNotifier) Connector.open(url);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			return;
		}
		synchronized(this) {
			if(!started) {
				try {
					scn.close();
				} catch(IOException e) {
					if(LOG.isLoggable(Level.WARNING))
						LOG.warning(e.getMessage());
				}
				return;
			}
			socket = scn;
		}
		startContactAccepterThread();
	}

	private synchronized String getUuid() {
		assert started;
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
		assert started;
		// Try to make the device discoverable (requires root on Linux)
		try {
			localDevice.setDiscoverable(DiscoveryAgent.GIAC);
			TransportProperties p = callback.getLocalProperties();
			p.put("address", localDevice.getBluetoothAddress());
			callback.setLocalProperties(p);
		} catch(BluetoothStateException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
		}
	}

	private void startContactAccepterThread() {
		new Thread() {
			@Override
			public void run() {
				acceptContactConnections();
			}
		}.start();
	}

	private void acceptContactConnections() {
		while(true) {
			StreamConnectionNotifier scn;
			StreamConnection s;
			synchronized(this) {
				if(!started) return;
				scn = socket;
			}
			try {
				s = scn.acceptAndOpen();
			} catch(IOException e) {
				// This is expected when the socket is closed
				if(LOG.isLoggable(Level.INFO)) LOG.info(e.getMessage());
				return;
			}
			callback.incomingConnectionCreated(
					new BluetoothTransportConnection(s));
		}
	}

	public boolean shouldPoll() {
		return true;
	}

	public long getPollingInterval() {
		return pollingInterval;
	}

	public synchronized void poll() {
		if(!started) return;
		executor.execute(createConnectors());
	}

	private Runnable createConnectors() {
		return new Runnable() {
			public void run() {
				connectAndCallBack();
			}
		};
	}

	private void connectAndCallBack() {
		Map<ContactId, String> discovered = discoverContactUrls();
		for(Entry<ContactId, String> e : discovered.entrySet()) {
			ContactId c = e.getKey();
			String url = e.getValue();
			StreamTransportConnection s = connect(c, url);
			if(s != null) callback.outgoingConnectionCreated(c, s);
		}
	}

	private Map<ContactId, String> discoverContactUrls() {
		DiscoveryAgent discoveryAgent;
		Map<ContactId, TransportProperties> remote;
		synchronized(this) {
			if(!started) return Collections.emptyMap();
			discoveryAgent = localDevice.getDiscoveryAgent();
			remote = callback.getRemoteProperties();
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
		ContactListener listener = new ContactListener(discoveryAgent,
				addresses, uuids);
		synchronized(discoveryLock) {
			try {
				discoveryAgent.startInquiry(DiscoveryAgent.GIAC, listener);
				return listener.waitForUrls();
			} catch(BluetoothStateException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				return Collections.emptyMap();
			}
		}
	}

	private StreamTransportConnection connect(ContactId c, String url) {
		synchronized(this) {
			if(!started) return null;
		}
		try {
			if(LOG.isLoggable(Level.INFO)) LOG.info("Connecting to " + url);
			StreamConnection s = (StreamConnection) Connector.open(url);
			if(LOG.isLoggable(Level.INFO)) LOG.info("Connected");
			return new BluetoothTransportConnection(s);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			return null;
		}
	}

	public StreamTransportConnection createConnection(ContactId c) {
		String url = discoverContactUrls().get(c);
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
		// The invitee's device may not be discoverable, so both parties must
		// try to initiate connections
		String uuid = convertInvitationCodeToUuid(code);
		ConnectionCallback c = new ConnectionCallback(uuid, timeout);
		startOutgoingInvitationThread(c);
		startIncomingInvitationThread(c);
		StreamConnection s = c.waitForConnection();
		return s == null ? null : new BluetoothTransportConnection(s);
	}

	private String convertInvitationCodeToUuid(int code) {
		byte[] b = new byte[16];
		new Random(code).nextBytes(b);
		return StringUtils.toHexString(b);
	}

	private void startOutgoingInvitationThread(final ConnectionCallback c) {
		new Thread() {
			@Override
			public void run() {
				createInvitationConnection(c);
			}
		}.start();
	}

	private void createInvitationConnection(ConnectionCallback c) {
		DiscoveryAgent discoveryAgent;
		synchronized(this) {
			if(!started) return;
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
						LOG.warning(e.getMessage());
					return;
				}
			}
			synchronized(this) {
				if(!started) return;
			}
		}
		if(url == null) return;
		// Try to connect to the other party
		try {
			StreamConnection s = (StreamConnection) Connector.open(url);
			c.addConnection(s);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
		}
	}

	private void startIncomingInvitationThread(final ConnectionCallback c) {
		new Thread() {
			@Override
			public void run() {
				bindInvitationSocket(c);
			}
		}.start();
	}

	private void bindInvitationSocket(ConnectionCallback c) {
		synchronized(this) {
			if(!started) return;
			makeDeviceDiscoverable();
		}
		// Bind the socket
		String url = "btspp://localhost:" + c.getUuid() + ";name=RFCOMM";
		StreamConnectionNotifier scn;
		try {
			scn = (StreamConnectionNotifier) Connector.open(url);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			return;
		}
		startInvitationAccepterThread(c, scn);
		// Close the socket when the invitation times out
		try {
			Thread.sleep(c.getTimeout());
			scn.close();
		} catch(InterruptedException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
		}
	}

	private void startInvitationAccepterThread(final ConnectionCallback c,
			final StreamConnectionNotifier scn) {
		new Thread() {
			@Override
			public void run() {
				acceptInvitationConnection(c, scn);
			}
		}.start();
	}

	private void acceptInvitationConnection(ConnectionCallback c,
			StreamConnectionNotifier scn) {
		synchronized(this) {
			if(!started) return;
		}
		try {
			StreamConnection s = scn.acceptAndOpen();
			c.addConnection(s);
		} catch(IOException e) {
			// This is expected when the socket is closed
			if(LOG.isLoggable(Level.INFO)) LOG.info(e.getMessage());
		}
	}
}
