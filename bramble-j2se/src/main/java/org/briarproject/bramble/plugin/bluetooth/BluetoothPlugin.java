package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.keyagreement.KeyAgreementConnection;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.util.OsUtils;
import org.briarproject.bramble.util.StringUtils;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.LocalDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static javax.bluetooth.DiscoveryAgent.GIAC;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.TRANSPORT_ID_BLUETOOTH;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.ID;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_ADDRESS;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_UUID;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.UUID_BYTES;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class BluetoothPlugin implements DuplexPlugin {

	private static final Logger LOG =
			Logger.getLogger(BluetoothPlugin.class.getName());

	private final Executor ioExecutor;
	private final SecureRandom secureRandom;
	private final Backoff backoff;
	private final DuplexPluginCallback callback;
	private final int maxLatency;
	private final AtomicBoolean used = new AtomicBoolean(false);

	private volatile boolean running = false;
	private volatile StreamConnectionNotifier socket = null;
	private volatile LocalDevice localDevice = null;

	BluetoothPlugin(Executor ioExecutor, SecureRandom secureRandom,
			Backoff backoff, DuplexPluginCallback callback, int maxLatency) {
		this.ioExecutor = ioExecutor;
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
	public void start() throws PluginException {
		if (used.getAndSet(true)) throw new IllegalStateException();
		// Initialise the Bluetooth stack
		try {
			localDevice = LocalDevice.getLocalDevice();
		} catch (UnsatisfiedLinkError e) {
			// On Linux the user may need to install libbluetooth-dev
			if (OsUtils.isLinux())
				callback.showMessage("BLUETOOTH_INSTALL_LIBS");
			throw new PluginException(e);
		} catch (BluetoothStateException e) {
			throw new PluginException(e);
		}
		if (LOG.isLoggable(INFO))
			LOG.info("Local address " + localDevice.getBluetoothAddress());
		running = true;
		bind();
	}

	private void bind() {
		ioExecutor.execute(() -> {
			if (!running) return;
			// Advertise the Bluetooth address to contacts
			TransportProperties p = new TransportProperties();
			p.put(PROP_ADDRESS, localDevice.getBluetoothAddress());
			callback.mergeLocalProperties(p);
			// Bind a server socket to accept connections from contacts
			String url = makeUrl("localhost", getUuid());
			StreamConnectionNotifier ss;
			try {
				ss = (StreamConnectionNotifier) Connector.open(url);
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING))
					LOG.log(WARNING, e.toString(), e);
				return;
			}
			if (!running) {
				tryToClose(ss);
				return;
			}
			socket = ss;
			backoff.reset();
			callback.transportEnabled();
			acceptContactConnections(ss);
		});
	}

	private String makeUrl(String address, String uuid) {
		return "btspp://" + address + ":" + uuid + ";name=RFCOMM";
	}

	private String getUuid() {
		String uuid = callback.getLocalProperties().get(PROP_UUID);
		if (uuid == null) {
			byte[] random = new byte[UUID_BYTES];
			secureRandom.nextBytes(random);
			uuid = UUID.nameUUIDFromBytes(random).toString();
			TransportProperties p = new TransportProperties();
			p.put(PROP_UUID, uuid);
			callback.mergeLocalProperties(p);
		}
		return uuid;
	}

	private void tryToClose(@Nullable StreamConnectionNotifier ss) {
		try {
			if (ss != null) ss.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} finally {
			callback.transportDisabled();
		}
	}

	private void acceptContactConnections(StreamConnectionNotifier ss) {
		while (true) {
			StreamConnection s;
			try {
				s = ss.acceptAndOpen();
			} catch (IOException e) {
				// This is expected when the socket is closed
				if (LOG.isLoggable(INFO)) LOG.info(e.toString());
				return;
			}
			backoff.reset();
			callback.incomingConnectionCreated(wrapSocket(s));
			if (!running) return;
		}
	}

	private DuplexTransportConnection wrapSocket(StreamConnection s) {
		return new BluetoothTransportConnection(this, s);
	}

	@Override
	public void stop() {
		running = false;
		tryToClose(socket);
	}

	@Override
	public boolean isRunning() {
		return running;
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
		if (!running) return;
		backoff.increment();
		// Try to connect to known devices in parallel
		Map<ContactId, TransportProperties> remote =
				callback.getRemoteProperties();
		for (Entry<ContactId, TransportProperties> e : remote.entrySet()) {
			ContactId c = e.getKey();
			if (connected.contains(c)) continue;
			String address = e.getValue().get(PROP_ADDRESS);
			if (StringUtils.isNullOrEmpty(address)) continue;
			String uuid = e.getValue().get(PROP_UUID);
			if (StringUtils.isNullOrEmpty(uuid)) continue;
			ioExecutor.execute(() -> {
				if (!running) return;
				StreamConnection s = connect(makeUrl(address, uuid));
				if (s != null) {
					backoff.reset();
					callback.outgoingConnectionCreated(c, wrapSocket(s));
				}
			});
		}
	}

	@Nullable
	private StreamConnection connect(String url) {
		if (LOG.isLoggable(INFO)) LOG.info("Connecting to " + url);
		try {
			StreamConnection s = (StreamConnection) Connector.open(url);
			if (LOG.isLoggable(INFO)) LOG.info("Connected to " + url);
			return s;
		} catch (IOException e) {
			if (LOG.isLoggable(INFO)) LOG.info("Could not connect to " + url);
			return null;
		}
	}

	@Override
	public DuplexTransportConnection createConnection(ContactId c) {
		if (!running) return null;
		TransportProperties p = callback.getRemoteProperties(c);
		String address = p.get(PROP_ADDRESS);
		if (StringUtils.isNullOrEmpty(address)) return null;
		String uuid = p.get(PROP_UUID);
		if (StringUtils.isNullOrEmpty(uuid)) return null;
		String url = makeUrl(address, uuid);
		StreamConnection s = connect(url);
		if (s == null) return null;
		return new BluetoothTransportConnection(this, s);
	}

	@Override
	public boolean supportsKeyAgreement() {
		return true;
	}

	@Override
	public KeyAgreementListener createKeyAgreementListener(byte[] commitment) {
		if (!running) return null;
		// No truncation necessary because COMMIT_LENGTH = 16
		String uuid = UUID.nameUUIDFromBytes(commitment).toString();
		if (LOG.isLoggable(INFO)) LOG.info("Key agreement UUID " + uuid);
		String url = makeUrl("localhost", uuid);
		// Make the device discoverable if possible
		makeDeviceDiscoverable();
		// Bind a server socket for receiving key agreementconnections
		StreamConnectionNotifier ss;
		try {
			ss = (StreamConnectionNotifier) Connector.open(url);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return null;
		}
		if (!running) {
			tryToClose(ss);
			return null;
		}
		BdfList descriptor = new BdfList();
		descriptor.add(TRANSPORT_ID_BLUETOOTH);
		String address = localDevice.getBluetoothAddress();
		descriptor.add(StringUtils.macToBytes(address));
		return new BluetoothKeyAgreementListener(descriptor, ss);
	}

	@Override
	public DuplexTransportConnection createKeyAgreementConnection(
			byte[] commitment, BdfList descriptor, long timeout) {
		if (!isRunning()) return null;
		String address;
		try {
			address = parseAddress(descriptor);
		} catch (FormatException e) {
			LOG.info("Invalid address in key agreement descriptor");
			return null;
		}
		// No truncation necessary because COMMIT_LENGTH = 16
		String uuid = UUID.nameUUIDFromBytes(commitment).toString();
		if (LOG.isLoggable(INFO))
			LOG.info("Connecting to key agreement UUID " + uuid);
		String url = makeUrl(address, uuid);
		StreamConnection s = connect(url);
		if (s == null) return null;
		return new BluetoothTransportConnection(this, s);
	}

	private String parseAddress(BdfList descriptor) throws FormatException {
		byte[] mac = descriptor.getRaw(1);
		if (mac.length != 6) throw new FormatException();
		return StringUtils.macToString(mac);
	}

	private void makeDeviceDiscoverable() {
		// Try to make the device discoverable (requires root on Linux)
		try {
			localDevice.setDiscoverable(GIAC);
		} catch (BluetoothStateException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private class BluetoothKeyAgreementListener extends KeyAgreementListener {

		private final StreamConnectionNotifier ss;

		private BluetoothKeyAgreementListener(BdfList descriptor,
				StreamConnectionNotifier ss) {
			super(descriptor);
			this.ss = ss;
		}

		@Override
		public Callable<KeyAgreementConnection> listen() {
			return () -> {
				StreamConnection s = ss.acceptAndOpen();
				if (LOG.isLoggable(INFO))
					LOG.info(ID.getString() + ": Incoming connection");
				return new KeyAgreementConnection(
						new BluetoothTransportConnection(
								BluetoothPlugin.this, s), ID);
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
