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
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.StreamTransportCallback;
import net.sf.briar.api.plugins.StreamTransportPlugin;
import net.sf.briar.api.transport.StreamTransportConnection;
import net.sf.briar.plugins.AbstractPlugin;
import net.sf.briar.util.OsUtils;
import net.sf.briar.util.StringUtils;

class BluetoothPlugin extends AbstractPlugin implements StreamTransportPlugin {

	public static final int TRANSPORT_ID = 2;

	private static final TransportId id = new TransportId(TRANSPORT_ID);
	private static final Logger LOG =
		Logger.getLogger(BluetoothPlugin.class.getName());

	private final StreamTransportCallback callback;
	private final long pollingInterval;

	private LocalDevice localDevice = null;
	private StreamConnectionNotifier streamConnectionNotifier = null;

	BluetoothPlugin(Executor executor, StreamTransportCallback callback,
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
			if(OsUtils.isLinux())
				callback.showMessage("BLUETOOTH_INSTALL LIBS");
			throw new IOException(e.getMessage());
		}
		executor.execute(createBinder());
	}

	@Override
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
		LocalDevice ld;
		synchronized(this) {
			if(!started) return;
			TransportConfig c = callback.getConfig();
			uuid = c.get("uuid");
			if(uuid == null) uuid = createAndSetUuid(c);
			ld = localDevice;
		}
		// Try to make the device discoverable (requires root on Linux)
		try {
			ld.setDiscoverable(DiscoveryAgent.GIAC);
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
			startListener();
		}
		setLocalBluetoothAddress(ld.getBluetoothAddress());
	}

	private synchronized String createAndSetUuid(TransportConfig c) {
		byte[] b = new byte[16];
		new Random().nextBytes(b); // FIXME: Use a SecureRandom?
		String uuid = StringUtils.toHexString(b);
		c.put("uuid", uuid);
		callback.setConfig(c);
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
			BluetoothTransportConnection conn =
				new BluetoothTransportConnection(s);
			callback.incomingConnectionCreated(conn);
		}
	}

	private synchronized void setLocalBluetoothAddress(String address) {
		if(!started) return;
		TransportProperties p = callback.getLocalProperties();
		p.put("address", address);
		callback.setLocalProperties(p);
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
		Map<ContactId, String> discovered = discover();
		for(Entry<ContactId, String> e : discovered.entrySet()) {
			ContactId c = e.getKey();
			String url = e.getValue();
			StreamTransportConnection conn = connect(c, url);
			if(conn != null) callback.outgoingConnectionCreated(c, conn);
		}
	}

	private Map<ContactId, String> discover() {
		DiscoveryAgent discoveryAgent;
		Map<String, ContactId> addresses;
		Map<ContactId, String> uuids;
		synchronized(this) {
			if(!started) return Collections.emptyMap();
			if(localDevice == null) return Collections.emptyMap();
			discoveryAgent = localDevice.getDiscoveryAgent();
			addresses = new HashMap<String, ContactId>();
			uuids = new HashMap<ContactId, String>();
			Map<ContactId, TransportProperties> remote =
				callback.getRemoteProperties();
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

	private StreamTransportConnection connect(ContactId c, String url) {
		try {
			synchronized(this) {
				if(!started) return null;
				Map<ContactId, TransportProperties> remote =
					callback.getRemoteProperties();
				if(!remote.containsKey(c)) return null;
			}
			StreamConnection s = (StreamConnection) Connector.open(url);
			return new BluetoothTransportConnection(s);
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			return null;
		}
	}

	public StreamTransportConnection createConnection(ContactId c) {
		Map<ContactId, String> discovered = discover();
		String url = discovered.get(c);
		return url == null ? null : connect(c, url);
	}
}
