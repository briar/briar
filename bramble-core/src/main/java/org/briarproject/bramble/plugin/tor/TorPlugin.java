package org.briarproject.bramble.plugin.tor;

import net.freehaven.tor.control.EventHandler;
import net.freehaven.tor.control.TorControlConnection;

import org.briarproject.bramble.PoliteExecutor;
import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.battery.event.BatteryEvent;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.network.NetworkStatus;
import org.briarproject.bramble.api.network.event.NetworkStatusEvent;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.ConnectionHandler;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.rendezvous.KeyMaterialSource;
import org.briarproject.bramble.api.rendezvous.RendezvousEndpoint;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.event.SettingsUpdatedEvent;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.net.SocketFactory;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static net.freehaven.tor.control.TorControlCommands.HS_ADDRESS;
import static net.freehaven.tor.control.TorControlCommands.HS_PRIVKEY;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.DISABLED;
import static org.briarproject.bramble.api.plugin.Plugin.State.ENABLING;
import static org.briarproject.bramble.api.plugin.Plugin.State.INACTIVE;
import static org.briarproject.bramble.api.plugin.TorConstants.CONTROL_PORT;
import static org.briarproject.bramble.api.plugin.TorConstants.ID;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_MOBILE;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK_AUTOMATIC;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK_NEVER;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK_WITH_BRIDGES;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_ONLY_WHEN_CHARGING;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_PORT;
import static org.briarproject.bramble.api.plugin.TorConstants.PROP_ONION_V2;
import static org.briarproject.bramble.api.plugin.TorConstants.PROP_ONION_V3;
import static org.briarproject.bramble.api.plugin.TorConstants.REASON_BATTERY;
import static org.briarproject.bramble.api.plugin.TorConstants.REASON_COUNTRY_BLOCKED;
import static org.briarproject.bramble.api.plugin.TorConstants.REASON_MOBILE_DATA;
import static org.briarproject.bramble.api.plugin.TorConstants.REASON_USER;
import static org.briarproject.bramble.plugin.tor.TorRendezvousCrypto.SEED_BYTES;
import static org.briarproject.bramble.util.IoUtils.copyAndClose;
import static org.briarproject.bramble.util.IoUtils.tryToClose;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.PrivacyUtils.scrubOnion;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
abstract class TorPlugin implements DuplexPlugin, EventHandler, EventListener {

	private static final Logger LOG = getLogger(TorPlugin.class.getName());

	private static final String[] EVENTS = {
			"CIRC", "ORCONN", "HS_DESC", "NOTICE", "WARN", "ERR"
	};
	private static final String OWNER = "__OwningControllerProcess";
	private static final int COOKIE_TIMEOUT_MS = 3000;
	private static final int COOKIE_POLLING_INTERVAL_MS = 200;
	private static final Pattern ONION_V2 = Pattern.compile("[a-z2-7]{16}");
	private static final Pattern ONION_V3 = Pattern.compile("[a-z2-7]{56}");

	private final Executor ioExecutor, connectionStatusExecutor;
	private final NetworkManager networkManager;
	private final LocationUtils locationUtils;
	private final SocketFactory torSocketFactory;
	private final Clock clock;
	private final BatteryManager batteryManager;
	private final Backoff backoff;
	private final TorRendezvousCrypto torRendezvousCrypto;
	private final PluginCallback callback;
	private final String architecture;
	private final CircumventionProvider circumventionProvider;
	private final ResourceProvider resourceProvider;
	private final int maxLatency, maxIdleTime, socketTimeout;
	private final File torDirectory, torFile, geoIpFile, obfs4File, configFile;
	private final File doneFile, cookieFile;
	private final AtomicBoolean used = new AtomicBoolean(false);

	protected final PluginState state = new PluginState();

	private volatile Socket controlSocket = null;
	private volatile TorControlConnection controlConnection = null;
	private volatile Settings settings = null;

	protected abstract int getProcessId();

	protected abstract long getLastUpdateTime();

	TorPlugin(Executor ioExecutor, NetworkManager networkManager,
			LocationUtils locationUtils, SocketFactory torSocketFactory,
			Clock clock, ResourceProvider resourceProvider,
			CircumventionProvider circumventionProvider,
			BatteryManager batteryManager, Backoff backoff,
			TorRendezvousCrypto torRendezvousCrypto,
			PluginCallback callback, String architecture, int maxLatency,
			int maxIdleTime, File torDirectory) {
		this.ioExecutor = ioExecutor;
		this.networkManager = networkManager;
		this.locationUtils = locationUtils;
		this.torSocketFactory = torSocketFactory;
		this.clock = clock;
		this.resourceProvider = resourceProvider;
		this.circumventionProvider = circumventionProvider;
		this.batteryManager = batteryManager;
		this.backoff = backoff;
		this.torRendezvousCrypto = torRendezvousCrypto;
		this.callback = callback;
		this.architecture = architecture;
		this.maxLatency = maxLatency;
		this.maxIdleTime = maxIdleTime;
		if (maxIdleTime > Integer.MAX_VALUE / 2)
			socketTimeout = Integer.MAX_VALUE;
		else socketTimeout = maxIdleTime * 2;
		this.torDirectory = torDirectory;
		torFile = new File(torDirectory, "tor");
		geoIpFile = new File(torDirectory, "geoip");
		obfs4File = new File(torDirectory, "obfs4proxy");
		configFile = new File(torDirectory, "torrc");
		doneFile = new File(torDirectory, "done");
		cookieFile = new File(torDirectory, ".tor/control_auth_cookie");
		// Don't execute more than one connection status check at a time
		connectionStatusExecutor =
				new PoliteExecutor("TorPlugin", ioExecutor, 1);
	}

	@Override
	public TransportId getId() {
		return TorConstants.ID;
	}

	@Override
	public int getMaxLatency() {
		return maxLatency;
	}

	@Override
	public int getMaxIdleTime() {
		return maxIdleTime;
	}

	@Override
	public void start() throws PluginException {
		if (used.getAndSet(true)) throw new IllegalStateException();
		state.setStarted();
		if (!torDirectory.exists()) {
			if (!torDirectory.mkdirs()) {
				LOG.warning("Could not create Tor directory.");
				throw new PluginException();
			}
		}
		// Load the settings
		settings = callback.getSettings();
		// Install or update the assets if necessary
		if (!assetsAreUpToDate()) installAssets();
		if (cookieFile.exists() && !cookieFile.delete())
			LOG.warning("Old auth cookie not deleted");
		// Start a new Tor process
		LOG.info("Starting Tor");
		String torPath = torFile.getAbsolutePath();
		String configPath = configFile.getAbsolutePath();
		String pid = String.valueOf(getProcessId());
		Process torProcess;
		ProcessBuilder pb =
				new ProcessBuilder(torPath, "-f", configPath, OWNER, pid);
		Map<String, String> env = pb.environment();
		env.put("HOME", torDirectory.getAbsolutePath());
		pb.directory(torDirectory);
		try {
			torProcess = pb.start();
		} catch (SecurityException | IOException e) {
			throw new PluginException(e);
		}
		// Log the process's standard output until it detaches
		if (LOG.isLoggable(INFO)) {
			Scanner stdout = new Scanner(torProcess.getInputStream());
			Scanner stderr = new Scanner(torProcess.getErrorStream());
			while (stdout.hasNextLine() || stderr.hasNextLine()) {
				if (stdout.hasNextLine()) {
					LOG.info(stdout.nextLine());
				}
				if (stderr.hasNextLine()) {
					LOG.info(stderr.nextLine());
				}
			}
			stdout.close();
			stderr.close();
		}
		try {
			// Wait for the process to detach or exit
			int exit = torProcess.waitFor();
			if (exit != 0) {
				if (LOG.isLoggable(WARNING))
					LOG.warning("Tor exited with value " + exit);
				throw new PluginException();
			}
			// Wait for the auth cookie file to be created/updated
			long start = clock.currentTimeMillis();
			while (cookieFile.length() < 32) {
				if (clock.currentTimeMillis() - start > COOKIE_TIMEOUT_MS) {
					LOG.warning("Auth cookie not created");
					if (LOG.isLoggable(INFO)) listFiles(torDirectory);
					throw new PluginException();
				}
				Thread.sleep(COOKIE_POLLING_INTERVAL_MS);
			}
			LOG.info("Auth cookie created");
		} catch (InterruptedException e) {
			LOG.warning("Interrupted while starting Tor");
			Thread.currentThread().interrupt();
			throw new PluginException();
		}
		try {
			// Open a control connection and authenticate using the cookie file
			controlSocket = new Socket("127.0.0.1", CONTROL_PORT);
			controlConnection = new TorControlConnection(controlSocket);
			controlConnection.authenticate(read(cookieFile));
			// Tell Tor to exit when the control connection is closed
			controlConnection.takeOwnership();
			controlConnection.resetConf(singletonList(OWNER));
			// Register to receive events from the Tor process
			controlConnection.setEventHandler(this);
			controlConnection.setEvents(asList(EVENTS));
			// Check whether Tor has already bootstrapped
			String phase = controlConnection.getInfo("status/bootstrap-phase");
			if (phase != null && phase.contains("PROGRESS=100")) {
				LOG.info("Tor has already bootstrapped");
				state.setBootstrapped();
			}
		} catch (IOException e) {
			throw new PluginException(e);
		}
		state.setTorStarted();
		// Check whether we're online
		updateConnectionStatus(networkManager.getNetworkStatus(),
				batteryManager.isCharging());
		// Bind a server socket to receive incoming hidden service connections
		bind();
	}

	private boolean assetsAreUpToDate() {
		return doneFile.lastModified() > getLastUpdateTime();
	}

	private void installAssets() throws PluginException {
		InputStream in = null;
		OutputStream out = null;
		try {
			// The done file may already exist from a previous installation
			//noinspection ResultOfMethodCallIgnored
			doneFile.delete();
			// Unzip the Tor binary to the filesystem
			in = getTorInputStream();
			out = new FileOutputStream(torFile);
			copyAndClose(in, out);
			// Make the Tor binary executable
			if (!torFile.setExecutable(true, true)) throw new IOException();
			// Unzip the GeoIP database to the filesystem
			in = getGeoIpInputStream();
			out = new FileOutputStream(geoIpFile);
			copyAndClose(in, out);
			// Unzip the Obfs4 proxy to the filesystem
			in = getObfs4InputStream();
			out = new FileOutputStream(obfs4File);
			copyAndClose(in, out);
			// Make the Obfs4 proxy executable
			if (!obfs4File.setExecutable(true, true)) throw new IOException();
			// Copy the config file to the filesystem
			in = getConfigInputStream();
			out = new FileOutputStream(configFile);
			copyAndClose(in, out);
			if (!doneFile.createNewFile())
				LOG.warning("Failed to create done file");
		} catch (IOException e) {
			tryToClose(in, LOG, WARNING);
			tryToClose(out, LOG, WARNING);
			throw new PluginException(e);
		}
	}

	private InputStream getTorInputStream() throws IOException {
		if (LOG.isLoggable(INFO))
			LOG.info("Installing Tor binary for " + architecture);
		InputStream in = resourceProvider
				.getResourceInputStream("tor_" + architecture, ".zip");
		ZipInputStream zin = new ZipInputStream(in);
		if (zin.getNextEntry() == null) throw new IOException();
		return zin;
	}

	private InputStream getGeoIpInputStream() throws IOException {
		InputStream in = resourceProvider.getResourceInputStream("geoip",
				".zip");
		ZipInputStream zin = new ZipInputStream(in);
		if (zin.getNextEntry() == null) throw new IOException();
		return zin;
	}

	private InputStream getObfs4InputStream() throws IOException {
		if (LOG.isLoggable(INFO))
			LOG.info("Installing obfs4proxy binary for " + architecture);
		InputStream in = resourceProvider
				.getResourceInputStream("obfs4proxy_" + architecture, ".zip");
		ZipInputStream zin = new ZipInputStream(in);
		if (zin.getNextEntry() == null) throw new IOException();
		return zin;
	}

	private InputStream getConfigInputStream() {
		ClassLoader cl = getClass().getClassLoader();
		return requireNonNull(cl.getResourceAsStream("torrc"));
	}

	private void listFiles(File f) {
		if (f.isDirectory()) {
			File[] children = f.listFiles();
			if (children != null) for (File child : children) listFiles(child);
		} else {
			LOG.info(f.getAbsolutePath() + " " + f.length());
		}
	}

	private byte[] read(File f) throws IOException {
		byte[] b = new byte[(int) f.length()];
		FileInputStream in = new FileInputStream(f);
		try {
			int offset = 0;
			while (offset < b.length) {
				int read = in.read(b, offset, b.length - offset);
				if (read == -1) throw new EOFException();
				offset += read;
			}
			return b;
		} finally {
			tryToClose(in, LOG, WARNING);
		}
	}

	private void bind() {
		ioExecutor.execute(() -> {
			// If there's already a port number stored in config, reuse it
			String portString = settings.get(PREF_TOR_PORT);
			int port;
			if (isNullOrEmpty(portString)) port = 0;
			else port = Integer.parseInt(portString);
			// Bind a server socket to receive connections from Tor
			ServerSocket ss = null;
			try {
				ss = new ServerSocket();
				ss.bind(new InetSocketAddress("127.0.0.1", port));
			} catch (IOException e) {
				logException(LOG, WARNING, e);
				tryToClose(ss, LOG, WARNING);
				return;
			}
			if (!state.setServerSocket(ss)) {
				LOG.info("Closing redundant server socket");
				tryToClose(ss, LOG, WARNING);
				return;
			}
			// Store the port number
			String localPort = String.valueOf(ss.getLocalPort());
			Settings s = new Settings();
			s.put(PREF_TOR_PORT, localPort);
			callback.mergeSettings(s);
			// Create a hidden service if necessary
			ioExecutor.execute(() -> publishHiddenService(localPort));
			backoff.reset();
			// Accept incoming hidden service connections from Tor
			acceptContactConnections(ss);
		});
	}

	private void publishHiddenService(String port) {
		if (!state.isTorRunning()) return;
		LOG.info("Creating hidden service");
		String privKey = settings.get(HS_PRIVKEY);
		Map<Integer, String> portLines = singletonMap(80, "127.0.0.1:" + port);
		Map<String, String> response;
		try {
			// Use the control connection to set up the hidden service
			if (privKey == null)
				response = controlConnection.addOnion(portLines);
			else response = controlConnection.addOnion(privKey, portLines);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			return;
		}
		if (!response.containsKey(HS_ADDRESS)) {
			LOG.warning("Tor did not return a hidden service address");
			return;
		}
		if (privKey == null && !response.containsKey(HS_PRIVKEY)) {
			LOG.warning("Tor did not return a private key");
			return;
		}
		// Publish the hidden service's onion hostname in transport properties
		String onion2 = response.get(HS_ADDRESS);
		if (LOG.isLoggable(INFO))
			LOG.info("Hidden service " + scrubOnion(onion2));
		TransportProperties p = new TransportProperties();
		p.put(PROP_ONION_V2, onion2);
		callback.mergeLocalProperties(p);
		if (privKey == null) {
			// Save the hidden service's private key for next time
			Settings s = new Settings();
			s.put(HS_PRIVKEY, response.get(HS_PRIVKEY));
			callback.mergeSettings(s);
		}
	}

	private void acceptContactConnections(ServerSocket ss) {
		while (true) {
			Socket s;
			try {
				s = ss.accept();
				s.setSoTimeout(socketTimeout);
			} catch (IOException e) {
				// This is expected when the server socket is closed
				LOG.info("Server socket closed");
				state.clearServerSocket(ss);
				return;
			}
			LOG.info("Connection received");
			backoff.reset();
			callback.handleConnection(new TorTransportConnection(this, s));
		}
	}

	protected void enableNetwork(boolean enable) throws IOException {
		state.enableNetwork(enable);
		controlConnection.setConf("DisableNetwork", enable ? "0" : "1");
	}

	private void enableBridges(boolean enable, boolean needsMeek)
			throws IOException {
		if (enable) {
			Collection<String> conf = new ArrayList<>();
			conf.add("UseBridges 1");
			if (needsMeek) {
				conf.add("ClientTransportPlugin meek_lite exec " +
						obfs4File.getAbsolutePath());
			} else {
				conf.add("ClientTransportPlugin obfs4 exec " +
						obfs4File.getAbsolutePath());
			}
			conf.addAll(circumventionProvider.getBridges(needsMeek));
			controlConnection.setConf(conf);
		} else {
			controlConnection.setConf("UseBridges", "0");
		}
	}

	@Override
	public void stop() {
		ServerSocket ss = state.setStopped();
		tryToClose(ss, LOG, WARNING);
		if (controlSocket != null && controlConnection != null) {
			try {
				LOG.info("Stopping Tor");
				controlConnection.setConf("DisableNetwork", "1");
				controlConnection.shutdownTor("TERM");
				controlSocket.close();
			} catch (IOException e) {
				logException(LOG, WARNING, e);
			}
		}
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
		ioExecutor.execute(() -> {
			DuplexTransportConnection d = createConnection(p);
			if (d != null) {
				backoff.reset();
				h.handleConnection(d);
			}
		});
	}

	@Override
	public DuplexTransportConnection createConnection(TransportProperties p) {
		if (getState() != ACTIVE) return null;
		String bestOnion = null;
		String onion2 = p.get(PROP_ONION_V2);
		String onion3 = p.get(PROP_ONION_V3);
		if (!isNullOrEmpty(onion2)) {
			if (ONION_V2.matcher(onion2).matches()) {
				bestOnion = onion2;
			} else {
				// Don't scrub the address so we can find the problem
				if (LOG.isLoggable(INFO))
					LOG.info("Invalid v2 hostname: " + onion2);
			}
		}
		if (!isNullOrEmpty(onion3)) {
			if (ONION_V3.matcher(onion3).matches()) {
				bestOnion = onion3;
			} else {
				// Don't scrub the address so we can find the problem
				if (LOG.isLoggable(INFO))
					LOG.info("Invalid v3 hostname: " + onion3);
			}
		}
		if (bestOnion == null) return null;
		Socket s = null;
		try {
			if (LOG.isLoggable(INFO))
				LOG.info("Connecting to " + scrubOnion(bestOnion));
			s = torSocketFactory.createSocket(bestOnion + ".onion", 80);
			s.setSoTimeout(socketTimeout);
			if (LOG.isLoggable(INFO))
				LOG.info("Connected to " + scrubOnion(bestOnion));
			return new TorTransportConnection(this, s);
		} catch (IOException e) {
			if (LOG.isLoggable(INFO)) {
				LOG.info("Could not connect to " + scrubOnion(bestOnion)
						+ ": " + e.toString());
			}
			tryToClose(s, LOG, WARNING);
			return null;
		}
	}

	@Override
	public boolean supportsKeyAgreement() {
		return false;
	}

	@Override
	public KeyAgreementListener createKeyAgreementListener(byte[] commitment) {
		throw new UnsupportedOperationException();
	}

	@Override
	public DuplexTransportConnection createKeyAgreementConnection(
			byte[] commitment, BdfList descriptor) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean supportsRendezvous() {
		return true;
	}

	@Override
	public RendezvousEndpoint createRendezvousEndpoint(KeyMaterialSource k,
			boolean alice, ConnectionHandler incoming) {
		byte[] aliceSeed = k.getKeyMaterial(SEED_BYTES);
		byte[] bobSeed = k.getKeyMaterial(SEED_BYTES);
		byte[] localSeed = alice ? aliceSeed : bobSeed;
		byte[] remoteSeed = alice ? bobSeed : aliceSeed;
		String blob = torRendezvousCrypto.getPrivateKeyBlob(localSeed);
		String localOnion = torRendezvousCrypto.getOnionAddress(localSeed);
		String remoteOnion = torRendezvousCrypto.getOnionAddress(remoteSeed);
		TransportProperties remoteProperties = new TransportProperties();
		remoteProperties.put(PROP_ONION_V3, remoteOnion);
		try {
			ServerSocket ss = new ServerSocket();
			ss.bind(new InetSocketAddress("127.0.0.1", 0));
			int port = ss.getLocalPort();
			ioExecutor.execute(() -> {
				try {
					//noinspection InfiniteLoopStatement
					while (true) {
						Socket s = ss.accept();
						incoming.handleConnection(
								new TorTransportConnection(this, s));
					}
				} catch (IOException e) {
					// This is expected when the server socket is closed
					LOG.info("Rendezvous server socket closed");
				}
			});
			Map<Integer, String> portLines =
					singletonMap(80, "127.0.0.1:" + port);
			controlConnection.addOnion(blob, portLines);
			return new RendezvousEndpoint() {

				@Override
				public TransportProperties getRemoteTransportProperties() {
					return remoteProperties;
				}

				@Override
				public void close() throws IOException {
					controlConnection.delOnion(localOnion);
					tryToClose(ss, LOG, WARNING);
				}
			};
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			return null;
		}
	}

	@Override
	public void circuitStatus(String status, String id, String path) {
		if (status.equals("BUILT") &&
				state.getAndSetCircuitBuilt()) {
			LOG.info("First circuit built");
			backoff.reset();
		}
	}

	@Override
	public void streamStatus(String status, String id, String target) {
	}

	@Override
	public void orConnStatus(String status, String orName) {
		if (LOG.isLoggable(INFO))
			LOG.info("OR connection " + status + " " + orName);
		if (status.equals("CLOSED") || status.equals("FAILED")) {
			// Check whether we've lost connectivity
			updateConnectionStatus(networkManager.getNetworkStatus(),
					batteryManager.isCharging());
		}
	}

	@Override
	public void bandwidthUsed(long read, long written) {
	}

	@Override
	public void newDescriptors(List<String> orList) {
	}

	@Override
	public void message(String severity, String msg) {
		if (LOG.isLoggable(INFO)) LOG.info(severity + " " + msg);
		if (severity.equals("NOTICE") && msg.startsWith("Bootstrapped 100%")) {
			state.setBootstrapped();
			backoff.reset();
		}
	}

	@Override
	public void unrecognized(String type, String msg) {
		if (type.equals("HS_DESC") && msg.startsWith("UPLOADED"))
			LOG.info("Descriptor uploaded");
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof SettingsUpdatedEvent) {
			SettingsUpdatedEvent s = (SettingsUpdatedEvent) e;
			if (s.getNamespace().equals(ID.getString())) {
				LOG.info("Tor settings updated");
				settings = s.getSettings();
				// Works around a bug introduced in Tor 0.3.4.8.
				// https://trac.torproject.org/projects/tor/ticket/28027
				// Could be replaced with callback.transportDisabled()
				// when fixed.
				disableNetwork();
				updateConnectionStatus(networkManager.getNetworkStatus(),
						batteryManager.isCharging());
			}
		} else if (e instanceof NetworkStatusEvent) {
			updateConnectionStatus(((NetworkStatusEvent) e).getStatus(),
					batteryManager.isCharging());
		} else if (e instanceof BatteryEvent) {
			updateConnectionStatus(networkManager.getNetworkStatus(),
					((BatteryEvent) e).isCharging());
		}
	}

	private void disableNetwork() {
		connectionStatusExecutor.execute(() -> {
			try {
				if (state.isTorRunning()) enableNetwork(false);
			} catch (IOException ex) {
				logException(LOG, WARNING, ex);
			}
		});
	}

	private void updateConnectionStatus(NetworkStatus status,
			boolean charging) {
		connectionStatusExecutor.execute(() -> {
			if (!state.isTorRunning()) return;
			boolean online = status.isConnected();
			boolean wifi = status.isWifi();
			String country = locationUtils.getCurrentCountry();
			boolean blocked =
					circumventionProvider.isTorProbablyBlocked(country);
			int network = settings.getInt(PREF_TOR_NETWORK,
					PREF_TOR_NETWORK_AUTOMATIC);
			boolean useMobile = settings.getBoolean(PREF_TOR_MOBILE, true);
			boolean onlyWhenCharging =
					settings.getBoolean(PREF_TOR_ONLY_WHEN_CHARGING, false);
			boolean bridgesWork = circumventionProvider.doBridgesWork(country);
			boolean automatic = network == PREF_TOR_NETWORK_AUTOMATIC;

			if (LOG.isLoggable(INFO)) {
				LOG.info("Online: " + online + ", wifi: " + wifi);
				if (country.isEmpty()) LOG.info("Country code unknown");
				else LOG.info("Country code: " + country);
				LOG.info("Charging: " + charging);
			}

			boolean enableNetwork = false, enableBridges = false;
			boolean useMeek = false, enableConnectionPadding = false;
			boolean disabledBySettings = false;
			int reasonDisabled = REASON_STARTING_STOPPING;

			if (!online) {
				LOG.info("Disabling network, device is offline");
			} else if (network == PREF_TOR_NETWORK_NEVER) {
				LOG.info("Disabling network, user has disabled Tor");
				disabledBySettings = true;
				reasonDisabled = REASON_USER;
			} else if (!charging && onlyWhenCharging) {
				LOG.info("Disabling network, device is on battery");
				disabledBySettings = true;
				reasonDisabled = REASON_BATTERY;
			} else if (!useMobile && !wifi) {
				LOG.info("Disabling network, device is using mobile data");
				disabledBySettings = true;
				reasonDisabled = REASON_MOBILE_DATA;
			} else if (automatic && blocked && !bridgesWork) {
				LOG.info("Disabling network, country is blocked");
				disabledBySettings = true;
				reasonDisabled = REASON_COUNTRY_BLOCKED;
			} else {
				LOG.info("Enabling network");
				enableNetwork = true;
				if (network == PREF_TOR_NETWORK_WITH_BRIDGES ||
						(automatic && bridgesWork)) {
					if (circumventionProvider.needsMeek(country)) {
						LOG.info("Using meek bridges");
						enableBridges = true;
						useMeek = true;
					} else {
						LOG.info("Using obfs4 bridges");
						enableBridges = true;
					}
				} else {
					LOG.info("Not using bridges");
				}
				if (wifi && charging) {
					LOG.info("Enabling connection padding");
					enableConnectionPadding = true;
				} else {
					LOG.info("Disabling connection padding");
				}
			}


			state.setDisabledBySettings(disabledBySettings, reasonDisabled);

			try {
				if (enableNetwork) {
					enableBridges(enableBridges, useMeek);
					enableConnectionPadding(enableConnectionPadding);
				}
				enableNetwork(enableNetwork);
			} catch (IOException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void enableConnectionPadding(boolean enable) throws IOException {
		controlConnection.setConf("ConnectionPadding", enable ? "1" : "0");
	}

	@ThreadSafe
	@NotNullByDefault
	protected class PluginState {

		@GuardedBy("this")
		private boolean started = false,
				stopped = false,
				torStarted = false,
				networkInitialised = false,
				networkEnabled = false,
				bootstrapped = false,
				circuitBuilt = false,
				disabledBySettings = false;

		@GuardedBy("this")
		private int reasonDisabled = REASON_STARTING_STOPPING;

		@GuardedBy("this")
		@Nullable
		private ServerSocket serverSocket = null;

		synchronized void setStarted() {
			started = true;
			callback.pluginStateChanged(getState());
		}

		synchronized void setTorStarted() {
			torStarted = true;
		}

		@SuppressWarnings("BooleanMethodIsAlwaysInverted")
		synchronized boolean isTorRunning() {
			return torStarted && !stopped;
		}

		@Nullable
		synchronized ServerSocket setStopped() {
			stopped = true;
			ServerSocket ss = serverSocket;
			serverSocket = null;
			callback.pluginStateChanged(getState());
			return ss;
		}

		synchronized void setBootstrapped() {
			bootstrapped = true;
			callback.pluginStateChanged(getState());
		}

		synchronized boolean getAndSetCircuitBuilt() {
			boolean firstCircuit = !circuitBuilt;
			circuitBuilt = true;
			callback.pluginStateChanged(getState());
			return firstCircuit;
		}

		synchronized void enableNetwork(boolean enable) {
			networkInitialised = true;
			networkEnabled = enable;
			if (!enable) circuitBuilt = false;
			callback.pluginStateChanged(getState());
		}

		synchronized void setDisabledBySettings(boolean disabledBySettings,
				int reasonDisabled) {
			this.disabledBySettings = disabledBySettings;
			this.reasonDisabled = reasonDisabled;
			callback.pluginStateChanged(getState());
		}

		synchronized boolean setServerSocket(ServerSocket ss) {
			if (stopped || serverSocket != null) return false;
			serverSocket = ss;
			return true;
		}

		synchronized void clearServerSocket(ServerSocket ss) {
			if (serverSocket == ss) serverSocket = null;
		}

		synchronized State getState() {
			if (!started || stopped || disabledBySettings) return DISABLED;
			if (!networkInitialised) return ENABLING;
			if (!networkEnabled) return INACTIVE;
			return bootstrapped && circuitBuilt ? ACTIVE : ENABLING;
		}

		synchronized int getReasonDisabled() {
			return getState() == DISABLED ? reasonDisabled : -1;
		}
	}
}
