package net.sf.briar.plugins.bluetooth;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;
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
import net.sf.briar.api.transport.stream.StreamTransportCallback;
import net.sf.briar.api.transport.stream.StreamTransportConnection;
import net.sf.briar.api.transport.stream.StreamTransportPlugin;
import net.sf.briar.plugins.AbstractPlugin;
import net.sf.briar.util.OsUtils;
import net.sf.briar.util.StringUtils;

class BluetoothPlugin extends AbstractPlugin implements StreamTransportPlugin {

	public static final int TRANSPORT_ID = 2;

	private static final TransportId id = new TransportId(TRANSPORT_ID);
	private static final Logger LOG =
		Logger.getLogger(BluetoothPlugin.class.getName());

	private final long pollingInterval;

	private StreamTransportCallback callback = null;
	private LocalDevice localDevice = null;
	private StreamConnectionNotifier streamConnectionNotifier = null;

	BluetoothPlugin(Executor executor, long pollingInterval) {
		super(executor);
		this.pollingInterval = pollingInterval;
	}

	public TransportId getId() {
		return id;
	}

	public synchronized void start(Map<String, String> localProperties,
			Map<ContactId, Map<String, String>> remoteProperties,
			Map<String, String> config, StreamTransportCallback callback)
	throws IOException {
		super.start(localProperties, remoteProperties, config);
		this.callback = callback;
		// Initialise the Bluetooth stack
		try {
			localDevice = LocalDevice.getLocalDevice();
		} catch(UnsatisfiedLinkError e) {
			// On Linux the user may need to install libbluetooth-dev
			if(OsUtils.isLinux())
				callback.showMessage("BLUETOOTH_INSTALL LIBS");
			throw new IOException(e.getMessage());
		}
		executor.execute(createBinder());
	}

	public synchronized void stop() throws IOException {
		super.stop();
		if(streamConnectionNotifier != null) {
			streamConnectionNotifier.close();
			streamConnectionNotifier = null;
		}
	}

	private Runnable createBinder() {
		return new Runnable() {
			public void run() {
				bind();
			}
		};
	}

	private void bind() {
		String uuid;
		synchronized(this) {
			if(!started) return;
			uuid = config.get("uuid");
			if(uuid == null) uuid = createAndSetUuid();
		}
		// Try to make the device discoverable (requires root on Linux)
		try {
			localDevice.setDiscoverable(DiscoveryAgent.GIAC);
		} catch(BluetoothStateException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
		}
		// Bind the port
		String url = "btspp://localhost:" + uuid + ";name=" + uuid;
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
			streamConnectionNotifier = scn;
			setLocalBluetoothAddress(localDevice.getBluetoothAddress());
			startListener();
		}
	}

	private String createAndSetUuid() {
		assert started;
		byte[] b = new byte[16];
		new Random().nextBytes(b); // FIXME: Use a SecureRandom?
		String uuid = StringUtils.toHexString(b);
		Map<String, String> m = new TreeMap<String, String>(config);
		m.put("uuid", uuid);
		callback.setConfig(m);
		return uuid;
	}

	private void startListener() {
		assert started;
		new Thread() {
			@Override
			public void run() {
				listen();
			}
		}.start();
	}

	private void listen() {
		while(true) {
			StreamConnectionNotifier scn;
			StreamConnection s;
			synchronized(this) {
				if(!started) return;
				scn = streamConnectionNotifier;
			}
			try {
				s = scn.acceptAndOpen();
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
				return;
			}
			synchronized(this) {
				if(!started) {
					try {
						s.close();
					} catch(IOException e) {
						if(LOG.isLoggable(Level.WARNING))
							LOG.warning(e.getMessage());
					}
					return;
				}
				BluetoothTransportConnection conn =
					new BluetoothTransportConnection(s);
				callback.incomingConnectionCreated(conn);
			}
		}
	}

	private void setLocalBluetoothAddress(String address) {
		assert started;
		Map<String, String> m = new TreeMap<String, String>(localProperties);
		m.put("address", address);
		callback.setLocalProperties(m);
	}

	public boolean shouldPoll() {
		return true;
	}

	public long getPollingInterval() {
		return pollingInterval;
	}

	public synchronized void poll() {
		if(!started) return;
		executor.execute(createConnectors(remoteProperties.keySet()));
	}

	private Runnable createConnectors(final Collection<ContactId> contacts) {
		return new Runnable() {
			public void run() {
				connectAndCallBack(contacts);
			}
		};
	}

	private void connectAndCallBack(Collection<ContactId> contacts) {
		Map<ContactId, String> discovered = discover(contacts);
		for(Entry<ContactId, String> e : discovered.entrySet()) {
			ContactId c = e.getKey();
			String url = e.getValue();
			StreamTransportConnection conn = createConnection(c, url);
			if(conn != null) {
				synchronized(this) {
					if(started) callback.outgoingConnectionCreated(c, conn);
				}
			}
		}
	}

	private Map<ContactId, String> discover(Collection<ContactId> contacts) {
		DiscoveryAgent discoveryAgent;
		Map<String, ContactId> addresses;
		Map<ContactId, String> uuids;
		synchronized(this) {
			if(!started) return Collections.emptyMap();
			if(localDevice == null) return Collections.emptyMap();
			discoveryAgent = localDevice.getDiscoveryAgent();
			addresses = new HashMap<String, ContactId>();
			uuids = new HashMap<ContactId, String>();
			for(Entry<ContactId, Map<String, String>> e
					: remoteProperties.entrySet()) {
				ContactId c = e.getKey();
				Map<String, String> properties = e.getValue();
				String address = properties.get("address");
				String uuid = properties.get("uuid");
				if(address != null && uuid != null) {
					addresses.put(address, c);
					uuids.put(c, uuid);
				}
			}
		}
		BluetoothListener listener =
			new BluetoothListener(discoveryAgent, addresses, uuids);
		try {
			synchronized(listener) {
				discoveryAgent.startInquiry(DiscoveryAgent.GIAC, listener);
				listener.wait();
			}
		} catch(BluetoothStateException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
		} catch(InterruptedException ignored) {}
		return listener.getUrls();
	}

	private StreamTransportConnection createConnection(ContactId c,
			String url) {
		try {
			synchronized(this) {
				if(!started) return null;
				if(!remoteProperties.containsKey(c)) return null;
			}
			StreamConnection s = (StreamConnection) Connector.open(url);
			return new BluetoothTransportConnection(s);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			return null;
		}
	}

	public StreamTransportConnection createConnection(ContactId c) {
		Map<ContactId, String> discovered = discover(Collections.singleton(c));
		String url = discovered.get(c);
		return url == null ? null : createConnection(c, url);
	}
}
