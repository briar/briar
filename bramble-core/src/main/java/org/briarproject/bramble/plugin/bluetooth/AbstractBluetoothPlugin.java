package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.Multiset;
import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.keyagreement.KeyAgreementConnection;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementListeningEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementStoppedListeningEvent;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.ConnectionHandler;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.properties.event.RemoteTransportPropertiesUpdatedEvent;
import org.briarproject.bramble.api.rendezvous.KeyMaterialSource;
import org.briarproject.bramble.api.rendezvous.RendezvousEndpoint;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.event.SettingsUpdatedEvent;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.TRANSPORT_ID_BLUETOOTH;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.DEFAULT_PREF_ADDRESS_IS_REFLECTED;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.DEFAULT_PREF_EVER_CONNECTED;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.DEFAULT_PREF_PLUGIN_ENABLE;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.ID;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PREF_ADDRESS_IS_REFLECTED;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PREF_EVER_CONNECTED;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_ADDRESS;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_UUID;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.UUID_BYTES;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.DISABLED;
import static org.briarproject.bramble.api.plugin.Plugin.State.INACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.STARTING_STOPPING;
import static org.briarproject.bramble.api.properties.TransportPropertyConstants.REFLECTED_PROPERTY_PREFIX;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.PrivacyUtils.scrubMacAddress;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;
import static org.briarproject.bramble.util.StringUtils.macToBytes;
import static org.briarproject.bramble.util.StringUtils.macToString;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
abstract class AbstractBluetoothPlugin<S, SS> implements BluetoothPlugin,
		EventListener {

	private static final Logger LOG =
			getLogger(AbstractBluetoothPlugin.class.getName());

	private final BluetoothConnectionLimiter connectionLimiter;
	final BluetoothConnectionFactory<S> connectionFactory;

	private final Executor ioExecutor, wakefulIoExecutor;
	private final SecureRandom secureRandom;
	private final Backoff backoff;
	private final PluginCallback callback;
	private final long maxLatency;
	private final int maxIdleTime;
	private final AtomicBoolean used = new AtomicBoolean(false);
	private final AtomicBoolean everConnected = new AtomicBoolean(false);

	protected final PluginState state = new PluginState();
	protected final Semaphore discoverSemaphore = new Semaphore(1);

	private volatile String contactConnectionsUuid = null;

	abstract void initialiseAdapter() throws IOException;

	abstract boolean isAdapterEnabled();

	/**
	 * Returns the local Bluetooth address, or null if no valid address can
	 * be found.
	 */
	@Nullable
	abstract String getBluetoothAddress();

	abstract SS openServerSocket(String uuid) throws IOException;

	abstract void tryToClose(@Nullable SS ss);

	abstract DuplexTransportConnection acceptConnection(SS ss)
			throws IOException;

	abstract boolean isValidAddress(String address);

	abstract DuplexTransportConnection connectTo(String address, String uuid)
			throws IOException;

	@Nullable
	abstract DuplexTransportConnection discoverAndConnect(String uuid);

	AbstractBluetoothPlugin(BluetoothConnectionLimiter connectionLimiter,
			BluetoothConnectionFactory<S> connectionFactory,
			Executor ioExecutor,
			Executor wakefulIoExecutor,
			SecureRandom secureRandom,
			Backoff backoff,
			PluginCallback callback,
			long maxLatency,
			int maxIdleTime) {
		this.connectionLimiter = connectionLimiter;
		this.connectionFactory = connectionFactory;
		this.ioExecutor = ioExecutor;
		this.wakefulIoExecutor = wakefulIoExecutor;
		this.secureRandom = secureRandom;
		this.backoff = backoff;
		this.callback = callback;
		this.maxLatency = maxLatency;
		this.maxIdleTime = maxIdleTime;
	}

	void onAdapterEnabled() {
		LOG.info("Bluetooth enabled");
		// We may not have been able to get the local address before
		ioExecutor.execute(this::updateProperties);
		if (getState() == INACTIVE) bind();
	}

	void onAdapterDisabled() {
		LOG.info("Bluetooth disabled");
		connectionLimiter.allConnectionsClosed();
		// The server socket may not have been closed automatically
		SS ss = state.clearServerSocket();
		if (ss != null) {
			LOG.info("Closing server socket");
			tryToClose(ss);
		}
	}

	@Override
	public TransportId getId() {
		return ID;
	}

	@Override
	public long getMaxLatency() {
		return maxLatency;
	}

	@Override
	public int getMaxIdleTime() {
		return maxIdleTime;
	}

	@Override
	public void start() throws PluginException {
		if (used.getAndSet(true)) throw new IllegalStateException();
		Settings settings = callback.getSettings();
		boolean enabledByUser = settings.getBoolean(PREF_PLUGIN_ENABLE,
				DEFAULT_PREF_PLUGIN_ENABLE);
		everConnected.set(settings.getBoolean(PREF_EVER_CONNECTED,
				DEFAULT_PREF_EVER_CONNECTED));
		state.setStarted(enabledByUser);
		try {
			initialiseAdapter();
		} catch (IOException e) {
			throw new PluginException(e);
		}
		updateProperties();
		if (enabledByUser && isAdapterEnabled()) bind();
	}

	private void bind() {
		ioExecutor.execute(() -> {
			if (getState() != INACTIVE) return;
			// Bind a server socket to accept connections from contacts
			SS ss;
			try {
				ss = openServerSocket(contactConnectionsUuid);
			} catch (IOException e) {
				logException(LOG, WARNING, e);
				return;
			}
			if (!state.setServerSocket(ss)) {
				LOG.info("Closing redundant server socket");
				tryToClose(ss);
				return;
			}
			backoff.reset();
			acceptContactConnections(ss);
		});
	}

	private void updateProperties() {
		TransportProperties p = callback.getLocalProperties();
		String address = p.get(PROP_ADDRESS);
		String uuid = p.get(PROP_UUID);
		Settings s = callback.getSettings();
		boolean isReflected = s.getBoolean(PREF_ADDRESS_IS_REFLECTED,
				DEFAULT_PREF_ADDRESS_IS_REFLECTED);
		boolean changed = false;
		if (address == null || isReflected) {
			address = getBluetoothAddress();
			if (LOG.isLoggable(INFO)) {
				LOG.info("Local address " + scrubMacAddress(address));
			}
			if (address == null) {
				if (everConnected.get()) {
					address = getReflectedAddress();
					if (LOG.isLoggable(INFO)) {
						LOG.info("Reflected address " +
								scrubMacAddress(address));
					}
					if (address != null) {
						changed = true;
						isReflected = true;
					}
				}
			} else {
				changed = true;
				isReflected = false;
			}
		}
		if (uuid == null) {
			byte[] random = new byte[UUID_BYTES];
			secureRandom.nextBytes(random);
			uuid = UUID.nameUUIDFromBytes(random).toString();
			changed = true;
		}
		contactConnectionsUuid = uuid;
		if (changed) {
			p = new TransportProperties();
			// If we previously used a reflected address and there's no longer
			// a reflected address with enough votes to be used, we'll continue
			// to use the old reflected address until there's a new winner
			if (address != null) p.put(PROP_ADDRESS, address);
			p.put(PROP_UUID, uuid);
			callback.mergeLocalProperties(p);
			s = new Settings();
			s.putBoolean(PREF_ADDRESS_IS_REFLECTED, isReflected);
			callback.mergeSettings(s);
		}
	}

	@Nullable
	private String getReflectedAddress() {
		// Count the number of votes for each reflected address
		String key = REFLECTED_PROPERTY_PREFIX + PROP_ADDRESS;
		Multiset<String> votes = new Multiset<>();
		for (TransportProperties p : callback.getRemoteProperties()) {
			String address = p.get(key);
			if (address != null && isValidAddress(address)) votes.add(address);
		}
		// If an address gets more than half of the votes, accept it
		int total = votes.getTotal();
		for (String address : votes.keySet()) {
			if (votes.getCount(address) * 2 > total) return address;
		}
		return null;
	}

	private void acceptContactConnections(SS ss) {
		while (true) {
			DuplexTransportConnection conn;
			try {
				conn = acceptConnection(ss);
			} catch (IOException e) {
				// This is expected when the server socket is closed
				LOG.info("Server socket closed");
				state.clearServerSocket();
				return;
			}
			LOG.info("Connection received");
			connectionLimiter.connectionOpened(conn);
			backoff.reset();
			setEverConnected();
			callback.handleConnection(conn);
		}
	}

	private void setEverConnected() {
		if (!everConnected.getAndSet(true)) {
			ioExecutor.execute(() -> {
				Settings s = new Settings();
				s.putBoolean(PREF_EVER_CONNECTED, true);
				callback.mergeSettings(s);
				// Contacts may already have sent a reflected address
				updateProperties();
			});
		}
	}

	@Override
	public void stop() {
		SS ss = state.setStopped();
		tryToClose(ss);
	}

	@Override
	public State getState() {
		return state.getState();
	}

	@Override
	public int getReasonsDisabled() {
		return state.getReasonsDisabled();
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
	public void poll(Collection<Pair<TransportProperties, ConnectionHandler>>
			properties) {
		if (getState() != ACTIVE) return;
		backoff.increment();
		for (Pair<TransportProperties, ConnectionHandler> p : properties) {
			connect(p.getFirst(), p.getSecond());
		}
	}

	private void connect(TransportProperties p, ConnectionHandler h) {
		String address = p.get(PROP_ADDRESS);
		if (isNullOrEmpty(address)) return;
		String uuid = p.get(PROP_UUID);
		if (isNullOrEmpty(uuid)) return;
		wakefulIoExecutor.execute(() -> {
			DuplexTransportConnection d = createConnection(p);
			if (d != null) {
				backoff.reset();
				setEverConnected();
				h.handleConnection(d);
			}
		});
	}

	@Nullable
	private DuplexTransportConnection connect(String address, String uuid) {
		// Validate the address
		if (!isValidAddress(address)) {
			if (LOG.isLoggable(WARNING))
				// Not scrubbing here to be able to figure out the problem
				LOG.warning("Invalid address " + address);
			return null;
		}
		// Validate the UUID
		try {
			//noinspection ResultOfMethodCallIgnored
			UUID.fromString(uuid);
		} catch (IllegalArgumentException e) {
			if (LOG.isLoggable(WARNING)) LOG.warning("Invalid UUID " + uuid);
			return null;
		}
		if (LOG.isLoggable(INFO))
			LOG.info("Connecting to " + scrubMacAddress(address));
		try {
			DuplexTransportConnection conn = connectTo(address, uuid);
			if (LOG.isLoggable(INFO))
				LOG.info("Connected to " + scrubMacAddress(address));
			return conn;
		} catch (IOException e) {
			if (LOG.isLoggable(INFO))
				LOG.info("Could not connect to " + scrubMacAddress(address));
			return null;
		}
	}

	@Override
	public DuplexTransportConnection createConnection(TransportProperties p) {
		if (getState() != ACTIVE) return null;
		if (!connectionLimiter.canOpenContactConnection()) return null;
		String address = p.get(PROP_ADDRESS);
		if (isNullOrEmpty(address)) return null;
		String uuid = p.get(PROP_UUID);
		if (isNullOrEmpty(uuid)) return null;
		DuplexTransportConnection conn = connect(address, uuid);
		if (conn != null) connectionLimiter.connectionOpened(conn);
		return conn;
	}

	@Override
	public boolean supportsKeyAgreement() {
		return true;
	}

	@Override
	public KeyAgreementListener createKeyAgreementListener(byte[] commitment) {
		if (getState() != ACTIVE) return null;
		// No truncation necessary because COMMIT_LENGTH = 16
		String uuid = UUID.nameUUIDFromBytes(commitment).toString();
		if (LOG.isLoggable(INFO)) LOG.info("Key agreement UUID " + uuid);
		// Bind a server socket for receiving key agreement connections
		SS ss;
		try {
			ss = openServerSocket(uuid);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			return null;
		}
		if (getState() != ACTIVE) {
			tryToClose(ss);
			return null;
		}
		BdfList descriptor = new BdfList();
		descriptor.add(TRANSPORT_ID_BLUETOOTH);
		String address = getBluetoothAddress();
		if (address != null) descriptor.add(macToBytes(address));
		return new BluetoothKeyAgreementListener(descriptor, ss);
	}

	@Override
	public DuplexTransportConnection createKeyAgreementConnection(
			byte[] commitment, BdfList descriptor) {
		if (getState() != ACTIVE) return null;
		// No truncation necessary because COMMIT_LENGTH = 16
		String uuid = UUID.nameUUIDFromBytes(commitment).toString();
		DuplexTransportConnection conn;
		if (descriptor.size() == 1) {
			if (LOG.isLoggable(INFO)) {
				LOG.info("Discovering address for key agreement UUID " +
						uuid);
			}
			conn = discoverAndConnect(uuid);
		} else {
			String address;
			try {
				address = parseAddress(descriptor);
			} catch (FormatException e) {
				LOG.info("Invalid address in key agreement descriptor");
				return null;
			}
			if (LOG.isLoggable(INFO))
				LOG.info("Connecting to key agreement UUID " + uuid);
			conn = connect(address, uuid);
		}
		if (conn != null) {
			connectionLimiter.connectionOpened(conn);
			setEverConnected();
		}
		return conn;
	}

	private String parseAddress(BdfList descriptor) throws FormatException {
		byte[] mac = descriptor.getRaw(1);
		if (mac.length != 6) throw new FormatException();
		return macToString(mac);
	}

	@Override
	public boolean isDiscovering() {
		return discoverSemaphore.availablePermits() == 0;
	}

	@Override
	public void disablePolling() {
		connectionLimiter.startLimiting();
	}

	@Override
	public void enablePolling() {
		connectionLimiter.endLimiting();
	}

	@Override
	public DuplexTransportConnection discoverAndConnectForSetup(String uuid) {
		DuplexTransportConnection conn = discoverAndConnect(uuid);
		if (conn != null) {
			connectionLimiter.connectionOpened(conn);
			setEverConnected();
		}
		return conn;
	}

	@Override
	public boolean supportsRendezvous() {
		return false;
	}

	@Override
	public RendezvousEndpoint createRendezvousEndpoint(KeyMaterialSource k,
			boolean alice, ConnectionHandler incoming) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof SettingsUpdatedEvent) {
			SettingsUpdatedEvent s = (SettingsUpdatedEvent) e;
			if (s.getNamespace().equals(ID.getString()))
				ioExecutor.execute(() -> onSettingsUpdated(s.getSettings()));
		} else if (e instanceof KeyAgreementListeningEvent) {
			connectionLimiter.startLimiting();
		} else if (e instanceof KeyAgreementStoppedListeningEvent) {
			connectionLimiter.endLimiting();
		} else if (e instanceof RemoteTransportPropertiesUpdatedEvent) {
			RemoteTransportPropertiesUpdatedEvent r =
					(RemoteTransportPropertiesUpdatedEvent) e;
			if (r.getTransportId().equals(ID)) {
				ioExecutor.execute(this::updateProperties);
			}
		}
	}

	@IoExecutor
	private void onSettingsUpdated(Settings settings) {
		boolean enabledByUser = settings.getBoolean(PREF_PLUGIN_ENABLE,
				DEFAULT_PREF_PLUGIN_ENABLE);
		SS ss = state.setEnabledByUser(enabledByUser);
		State s = getState();
		if (ss != null) {
			LOG.info("Disabled by user, closing server socket");
			tryToClose(ss);
		} else if (s == INACTIVE) {
			if (isAdapterEnabled()) {
				LOG.info("Enabled by user, opening server socket");
				bind();
			} else {
				LOG.info("Enabled by user but adapter is disabled");
			}
		}
	}

	private class BluetoothKeyAgreementListener extends KeyAgreementListener {

		private final SS ss;

		private BluetoothKeyAgreementListener(BdfList descriptor, SS ss) {
			super(descriptor);
			this.ss = ss;
		}

		@Override
		public KeyAgreementConnection accept() throws IOException {
			DuplexTransportConnection conn = acceptConnection(ss);
			if (LOG.isLoggable(INFO)) LOG.info(ID + ": Incoming connection");
			connectionLimiter.connectionOpened(conn);
			return new KeyAgreementConnection(conn, ID);
		}

		@Override
		public void close() {
			tryToClose(ss);
		}
	}

	@ThreadSafe
	@NotNullByDefault
	private class PluginState {

		@GuardedBy("this")
		private boolean started = false,
				stopped = false,
				enabledByUser = false;

		@GuardedBy("this")
		@Nullable
		private SS serverSocket = null;

		private synchronized void setStarted(boolean enabledByUser) {
			started = true;
			this.enabledByUser = enabledByUser;
			callback.pluginStateChanged(getState());
		}

		@Nullable
		private synchronized SS setStopped() {
			stopped = true;
			SS ss = serverSocket;
			serverSocket = null;
			callback.pluginStateChanged(getState());
			return ss;
		}

		@Nullable
		private synchronized SS setEnabledByUser(boolean enabledByUser) {
			this.enabledByUser = enabledByUser;
			SS ss = null;
			if (!enabledByUser) {
				ss = serverSocket;
				serverSocket = null;
			}
			callback.pluginStateChanged(getState());
			return ss;
		}

		private synchronized boolean setServerSocket(SS ss) {
			if (stopped || serverSocket != null) return false;
			serverSocket = ss;
			callback.pluginStateChanged(getState());
			return true;
		}

		@Nullable
		private synchronized SS clearServerSocket() {
			SS ss = serverSocket;
			serverSocket = null;
			callback.pluginStateChanged(getState());
			return ss;
		}

		private synchronized State getState() {
			if (!started || stopped) return STARTING_STOPPING;
			if (!enabledByUser) return DISABLED;
			return serverSocket == null ? INACTIVE : ACTIVE;
		}

		private synchronized int getReasonsDisabled() {
			return getState() == DISABLED ? REASON_USER : 0;
		}
	}
}
