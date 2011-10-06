package net.sf.briar.plugins.bluetooth;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.transport.InvalidConfigException;
import net.sf.briar.api.transport.InvalidPropertiesException;
import net.sf.briar.api.transport.stream.StreamTransportCallback;
import net.sf.briar.api.transport.stream.StreamTransportConnection;
import net.sf.briar.api.transport.stream.StreamTransportPlugin;
import net.sf.briar.plugins.AbstractPlugin;

class BluetoothPlugin extends AbstractPlugin implements StreamTransportPlugin {

	public static final int TRANSPORT_ID = 2;

	private static final TransportId id = new TransportId(TRANSPORT_ID);

	private final String uuid;
	private final long pollingInterval;

	private StreamTransportCallback callback = null;
	private StreamConnectionNotifier streamConnectionNotifier = null;

	BluetoothPlugin(Executor executor, String uuid, long pollingInterval) {
		super(executor);
		this.uuid = uuid;
		this.pollingInterval = pollingInterval;
	}

	public TransportId getId() {
		return id;
	}

	public synchronized void start(Map<String, String> localProperties,
			Map<ContactId, Map<String, String>> remoteProperties,
			Map<String, String> config, StreamTransportCallback callback)
	throws InvalidPropertiesException, InvalidConfigException,
	IOException {
		super.start(localProperties, remoteProperties, config);
		this.callback = callback;
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
		synchronized(this) {
			if(!started) return;
		}
		// Initialise the Bluetooth stack
		LocalDevice localDevice = null;
		try {
			localDevice = LocalDevice.getLocalDevice();
		} catch(BluetoothStateException e) {
			// FIXME: Logging
			return;
		}
		// Try to make the device discoverable (requires root on Linux)
		try {
			localDevice.setDiscoverable(DiscoveryAgent.GIAC);
		} catch(BluetoothStateException e) {
			// FIXME: Logging
			try {
				localDevice.setDiscoverable(DiscoveryAgent.LIAC);
			} catch(BluetoothStateException e1) {
				// FIXME: Logging
			}
		}
		// Bind the port
		String url = "btspp://localhost:" + uuid + ";name=" + uuid;
		StreamConnectionNotifier scn;
		try {
			scn = (StreamConnectionNotifier) Connector.open(url);
		} catch(IOException e) {
			// FIXME: Logging
			return;
		}
		synchronized(this) {
			if(!started) return;
			streamConnectionNotifier = scn;
			setConnectionUrl(localDevice.getBluetoothAddress());
			startListener();
		}
	}

	private void startListener() {
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
				// FIXME: Logging
				return;
			}
			synchronized(this) {
				if(!started) {
					try {
						s.close();
					} catch(IOException e) {
						// FIXME: Logging
					}
					return;
				}
				BluetoothTransportConnection conn =
					new BluetoothTransportConnection(s);
				callback.incomingConnectionCreated(conn);
			}
		}
	}

	private void setConnectionUrl(String address) {
		// Update the local properties with the connection URL
		String url = "btspp://" + address + ":" + uuid + ";name=" + uuid;
		Map<String, String> m = new TreeMap<String, String>(localProperties);
		m.put("url", url);
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
		for(ContactId c : remoteProperties.keySet()) {
			executor.execute(createConnector(c));
		}
	}

	private Runnable createConnector(final ContactId c) {
		return new Runnable() {
			public void run() {
				connect(c);
			}
		};
	}

	private StreamTransportConnection connect(ContactId c) {
		StreamTransportConnection conn = createAndConnectSocket(c);
		if(conn != null) {
			synchronized(this) {
				if(started) callback.outgoingConnectionCreated(c, conn);
			}
		}
		return conn;
	}

	private StreamTransportConnection createAndConnectSocket(ContactId c) {
		try {
			String url;
			synchronized(this) {
				if(!started) return null;
				Map<String, String> properties = remoteProperties.get(c);
				if(properties == null) return null;
				url = properties.get("url");
				if(url == null) return null;
			}
			StreamConnection s = (StreamConnection) Connector.open(url);
			return new BluetoothTransportConnection(s);
		} catch(IOException e) {
			// FIXME: Logging
			return null;
		}
	}

	public StreamTransportConnection createConnection(ContactId c) {
		synchronized(this) {
			if(!started) return null;
		}
		return createAndConnectSocket(c);
	}
}
