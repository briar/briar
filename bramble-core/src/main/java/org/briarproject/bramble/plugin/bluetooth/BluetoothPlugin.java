package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.FormatException;
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
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.plugin.event.BluetoothEnabledEvent;
import org.briarproject.bramble.api.plugin.event.DisableBluetoothEvent;
import org.briarproject.bramble.api.plugin.event.EnableBluetoothEvent;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.rendezvous.KeyMaterialSource;
import org.briarproject.bramble.api.rendezvous.RendezvousEndpoint;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.event.SettingsUpdatedEvent;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.TRANSPORT_ID_BLUETOOTH;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.ID;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_ADDRESS;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_UUID;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.REASON_NO_BT_ADAPTER;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.UUID_BYTES;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.DISABLED;
import static org.briarproject.bramble.api.plugin.Plugin.State.INACTIVE;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.PrivacyUtils.scrubMacAddress;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;
import static org.briarproject.bramble.util.StringUtils.macToBytes;
import static org.briarproject.bramble.util.StringUtils.macToString;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
abstract class BluetoothPlugin<SS> implements DuplexPlugin, EventListener {

	private static final Logger LOG =
			getLogger(BluetoothPlugin.class.getName());

	final BluetoothConnectionLimiter connectionLimiter;

	private final Executor ioExecutor;
	private final SecureRandom secureRandom;
	private final Backoff backoff;
	private final PluginCallback callback;
	private final int maxLatency;
	private final AtomicBoolean used = new AtomicBoolean(false);

	protected final PluginState state = new PluginState();

	private volatile String contactConnectionsUuid = null;

	abstract void initialiseAdapter() throws IOException;

	abstract boolean isAdapterEnabled();

	abstract void enableAdapter();

	abstract void disableAdapterIfEnabledByUs();

	abstract void setEnabledByUs();

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

	BluetoothPlugin(BluetoothConnectionLimiter connectionLimiter,
			Executor ioExecutor, SecureRandom secureRandom,
			Backoff backoff, PluginCallback callback, int maxLatency) {
		this.connectionLimiter = connectionLimiter;
		this.ioExecutor = ioExecutor;
		this.secureRandom = secureRandom;
		this.backoff = backoff;
		this.callback = callback;
		this.maxLatency = maxLatency;
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
		try {
			initialiseAdapter();
		} catch (IOException e) {
			state.setNoAdapter();
			throw new PluginException(e);
		}
		updateProperties();
		Settings settings = callback.getSettings();
		boolean enabledByUser = settings.getBoolean(PREF_PLUGIN_ENABLE, false);
		state.setStarted(enabledByUser);
		if (enabledByUser) {
			if (isAdapterEnabled()) bind();
			else enableAdapter();
		}
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
		boolean changed = false;
		if (address == null) {
			address = getBluetoothAddress();
			if (LOG.isLoggable(INFO))
				LOG.info("Local address " + scrubMacAddress(address));
			if (!isNullOrEmpty(address)) {
				p.put(PROP_ADDRESS, address);
				changed = true;
			}
		}
		if (uuid == null) {
			byte[] random = new byte[UUID_BYTES];
			secureRandom.nextBytes(random);
			uuid = UUID.nameUUIDFromBytes(random).toString();
			p.put(PROP_UUID, uuid);
			changed = true;
		}
		contactConnectionsUuid = uuid;
		if (changed) callback.mergeLocalProperties(p);
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
			backoff.reset();
			if (connectionLimiter.contactConnectionOpened(conn))
				callback.handleConnection(conn);
		}
	}

	@Override
	public void stop() {
		SS ss = state.setStopped();
		tryToClose(ss);
		disableAdapterIfEnabledByUs();
	}

	@Override
	public State getState() {
		return state.getState();
	}

	@Override
	public int getReasonDisabled() {
		return state.getReasonDisabled();
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
		ioExecutor.execute(() -> {
			if (getState() != ACTIVE) return;
			if (!connectionLimiter.canOpenContactConnection()) return;
			DuplexTransportConnection d = createConnection(p);
			if (d != null) {
				backoff.reset();
				if (connectionLimiter.contactConnectionOpened(d))
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
		if (conn == null) return null;
		// TODO: Why don't we reset the backoff here?
		return connectionLimiter.contactConnectionOpened(conn) ? conn : null;
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
			if (LOG.isLoggable(INFO))
				LOG.info("Discovering address for key agreement UUID " + uuid);
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
		if (conn != null) connectionLimiter.keyAgreementConnectionOpened(conn);
		return conn;
	}

	private String parseAddress(BdfList descriptor) throws FormatException {
		byte[] mac = descriptor.getRaw(1);
		if (mac.length != 6) throw new FormatException();
		return macToString(mac);
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
		if (e instanceof EnableBluetoothEvent) {
			ioExecutor.execute(this::enableAdapter);
		} else if (e instanceof DisableBluetoothEvent) {
			ioExecutor.execute(this::disableAdapterIfEnabledByUs);
		} else if (e instanceof BluetoothEnabledEvent) {
			setEnabledByUs();
		} else if (e instanceof SettingsUpdatedEvent) {
			SettingsUpdatedEvent s = (SettingsUpdatedEvent) e;
			if (s.getNamespace().equals(ID.getString()))
				ioExecutor.execute(() -> onSettingsUpdated(s.getSettings()));
		} else if (e instanceof KeyAgreementListeningEvent) {
			ioExecutor.execute(connectionLimiter::keyAgreementStarted);
		} else if (e instanceof KeyAgreementStoppedListeningEvent) {
			ioExecutor.execute(connectionLimiter::keyAgreementEnded);
		}
	}

	@IoExecutor
	private void onSettingsUpdated(Settings settings) {
		boolean enabledByUser = settings.getBoolean(PREF_PLUGIN_ENABLE, false);
		SS ss = state.setEnabledByUser(enabledByUser);
		State s = getState();
		if (ss != null) {
			LOG.info("Disabled by user, closing server socket");
			tryToClose(ss);
			disableAdapterIfEnabledByUs();
		} else if (s == INACTIVE) {
			LOG.info("Enabled by user, opening server socket");
			if (isAdapterEnabled()) bind();
			else enableAdapter();
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
			connectionLimiter.keyAgreementConnectionOpened(conn);
			return new KeyAgreementConnection(conn, ID);
		}

		@Override
		public void close() {
			tryToClose(ss);
		}
	}

	@ThreadSafe
	@NotNullByDefault
	protected class PluginState {

		@GuardedBy("this")
		private boolean started = false,
				stopped = false,
				noAdapter = false,
				enabledByUser = false;

		@GuardedBy("this")
		@Nullable
		private SS serverSocket = null;

		synchronized void setStarted(boolean enabledByUser) {
			started = true;
			this.enabledByUser = enabledByUser;
			callback.pluginStateChanged(getState());
		}

		@Nullable
		synchronized SS setStopped() {
			stopped = true;
			SS ss = serverSocket;
			serverSocket = null;
			callback.pluginStateChanged(getState());
			return ss;
		}

		synchronized void setNoAdapter() {
			noAdapter = true;
			callback.pluginStateChanged(getState());
		}

		@Nullable
		synchronized SS setEnabledByUser(boolean enabledByUser) {
			this.enabledByUser = enabledByUser;
			SS ss = null;
			if (!enabledByUser) {
				ss = serverSocket;
				serverSocket = null;
			}
			callback.pluginStateChanged(getState());
			return ss;
		}

		synchronized boolean setServerSocket(SS ss) {
			if (stopped || serverSocket != null) return false;
			serverSocket = ss;
			callback.pluginStateChanged(getState());
			return true;
		}

		@Nullable
		synchronized SS clearServerSocket() {
			SS ss = serverSocket;
			serverSocket = null;
			callback.pluginStateChanged(getState());
			return ss;
		}

		synchronized State getState() {
			if (!started || stopped || !enabledByUser) return DISABLED;
			return serverSocket == null ? INACTIVE : ACTIVE;
		}

		synchronized int getReasonDisabled() {
			if (noAdapter && !stopped) return REASON_NO_BT_ADAPTER;
			if (!started || stopped) return REASON_STARTING_STOPPING;
			return enabledByUser ? -1 : REASON_USER;
		}
	}
}
