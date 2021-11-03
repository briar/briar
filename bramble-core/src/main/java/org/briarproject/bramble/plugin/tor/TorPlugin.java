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

import java.io.ByteArrayInputStream;
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
import static org.briarproject.bramble.api.plugin.Plugin.State.STARTING_STOPPING;
import static org.briarproject.bramble.api.plugin.TorConstants.DEFAULT_PREF_PLUGIN_ENABLE;
import static org.briarproject.bramble.api.plugin.TorConstants.DEFAULT_PREF_TOR_MOBILE;
import static org.briarproject.bramble.api.plugin.TorConstants.DEFAULT_PREF_TOR_NETWORK;
import static org.briarproject.bramble.api.plugin.TorConstants.DEFAULT_PREF_TOR_ONLY_WHEN_CHARGING;
import static org.briarproject.bramble.api.plugin.TorConstants.HS_PRIVATE_KEY_V2;
import static org.briarproject.bramble.api.plugin.TorConstants.HS_PRIVATE_KEY_V3;
import static org.briarproject.bramble.api.plugin.TorConstants.HS_V3_CREATED;
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
import static org.briarproject.bramble.api.plugin.TorConstants.V3_MIGRATION_PERIOD_MS;
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

	private final Executor ioExecutor, wakefulIoExecutor;
	private final Executor connectionStatusExecutor;
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
	private final long maxLatency;
	private final int maxIdleTime;
	private final int socketTimeout;
	private final File torDirectory, geoIpFile, configFile;
	private int torSocksPort;
	private int torControlPort;
	private final File doneFile, cookieFile;
	private final AtomicBoolean used = new AtomicBoolean(false);

	protected final PluginState state = new PluginState();

	private volatile Socket controlSocket = null;
	private volatile TorControlConnection controlConnection = null;
	private volatile Settings settings = null;

	protected abstract int getProcessId();

	protected abstract long getLastUpdateTime();

	TorPlugin(Executor ioExecutor,
			Executor wakefulIoExecutor,
			NetworkManager networkManager,
			LocationUtils locationUtils,
			SocketFactory torSocketFactory,
			Clock clock,
			ResourceProvider resourceProvider,
			CircumventionProvider circumventionProvider,
			BatteryManager batteryManager,
			Backoff backoff,
			TorRendezvousCrypto torRendezvousCrypto,
			PluginCallback callback,
			String architecture,
			long maxLatency,
			int maxIdleTime,
			File torDirectory,
			int torSocksPort,
			int torControlPort) {
		this.ioExecutor = ioExecutor;
		this.wakefulIoExecutor = wakefulIoExecutor;
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
		this.torSocksPort = torSocksPort;
		this.torControlPort = torControlPort;
		geoIpFile = new File(torDirectory, "geoip");
		configFile = new File(torDirectory, "torrc");
		doneFile = new File(torDirectory, "done");
		cookieFile = new File(torDirectory, ".tor/control_auth_cookie");
		// Don't execute more than one connection status check at a time
		connectionStatusExecutor =
				new PoliteExecutor("TorPlugin", ioExecutor, 1);
	}

	protected File getTorExecutableFile() {
		return new File(torDirectory, "tor");
	}

	protected File getObfs4ExecutableFile() {
		return new File(torDirectory, "obfs4proxy");
	}

	@Override
	public TransportId getId() {
		return TorConstants.ID;
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
		if (!torDirectory.exists()) {
			if (!torDirectory.mkdirs()) {
				LOG.warning("Could not create Tor directory.");
				throw new PluginException();
			}
		}
		// Load the settings
		settings = migrateSettings(callback.getSettings());
		// Install or update the assets if necessary
		if (!assetsAreUpToDate()) installAssets();
		if (cookieFile.exists() && !cookieFile.delete())
			LOG.warning("Old auth cookie not deleted");
		// Start a new Tor process
		LOG.info("Starting Tor");
		File torFile = getTorExecutableFile();
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
			controlSocket = new Socket("127.0.0.1", torControlPort);
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
		state.setStarted();
		// Check whether we're online
		updateConnectionStatus(networkManager.getNetworkStatus(),
				batteryManager.isCharging());
		// Bind a server socket to receive incoming hidden service connections
		bind();
	}

	// TODO: Remove after a reasonable migration period (added 2020-06-25)
	private Settings migrateSettings(Settings settings) {
		int network = settings.getInt(PREF_TOR_NETWORK,
				DEFAULT_PREF_TOR_NETWORK);
		if (network == PREF_TOR_NETWORK_NEVER) {
			settings.putInt(PREF_TOR_NETWORK, DEFAULT_PREF_TOR_NETWORK);
			settings.putBoolean(PREF_PLUGIN_ENABLE, false);
			callback.mergeSettings(settings);
		}
		return settings;
	}

	private boolean assetsAreUpToDate() {
		return doneFile.lastModified() > getLastUpdateTime();
	}

	private void installAssets() throws PluginException {
		try {
			// The done file may already exist from a previous installation
			//noinspection ResultOfMethodCallIgnored
			doneFile.delete();
			installTorExecutable();
			installObfs4Executable();
			extract(getGeoIpInputStream(), geoIpFile);
			extract(getConfigInputStream(), configFile);
			if (!doneFile.createNewFile())
				LOG.warning("Failed to create done file");
		} catch (IOException e) {
			throw new PluginException(e);
		}
	}

	protected void extract(InputStream in, File dest) throws IOException {
		OutputStream out = new FileOutputStream(dest);
		copyAndClose(in, out);
	}

	protected void installTorExecutable() throws IOException {
		if (LOG.isLoggable(INFO))
			LOG.info("Installing Tor binary for " + architecture);
		File torFile = getTorExecutableFile();
		extract(getTorInputStream(), torFile);
		if (!torFile.setExecutable(true, true)) throw new IOException();
	}

	protected void installObfs4Executable() throws IOException {
		if (LOG.isLoggable(INFO))
			LOG.info("Installing obfs4proxy binary for " + architecture);
		File obfs4File = getObfs4ExecutableFile();
		extract(getObfs4InputStream(), obfs4File);
		if (!obfs4File.setExecutable(true, true)) throw new IOException();
	}

	private InputStream getTorInputStream() throws IOException {
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
		InputStream in = resourceProvider
				.getResourceInputStream("obfs4proxy_" + architecture, ".zip");
		ZipInputStream zin = new ZipInputStream(in);
		if (zin.getNextEntry() == null) throw new IOException();
		return zin;
	}

	private InputStream getTorrc() {
		StringBuilder strb = new StringBuilder();
		append(strb, "ControlPort", torControlPort);
		append(strb, "CookieAuthentication", 1);
		append(strb, "DisableNetwork", 1);
		append(strb, "RunAsDaemon", 1);
		append(strb, "SafeSocks", 1);
		append(strb, "SocksPort", torSocksPort);

		return new ByteArrayInputStream(strb.toString().getBytes());
	}

	private static void append(StringBuilder strb, String name, int value) {
		strb.append(name);
		strb.append(" ");
		strb.append(value);
		strb.append("\n");
	}

	private InputStream getConfigInputStream() {
		return requireNonNull(getTorrc());
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
		// TODO: Remove support for v2 hidden services after a reasonable
		// migration period (migration started 2020-06-30)
		String privKey2 = settings.get(HS_PRIVATE_KEY_V2);
		String privKey3 = settings.get(HS_PRIVATE_KEY_V3);
		String v3Created = settings.get(HS_V3_CREATED);
		// Publish a v2 hidden service if we've already created one, and
		// either we've never published a v3 hidden service or we're still
		// in the migration period since first publishing it
		if (!isNullOrEmpty(privKey2)) {
			long now = clock.currentTimeMillis();
			long then = v3Created == null ? now : Long.parseLong(v3Created);
			if (now - then >= V3_MIGRATION_PERIOD_MS) retireV2HiddenService();
			else publishV2HiddenService(port, privKey2);
		}
		publishV3HiddenService(port, privKey3);
	}

	private void publishV2HiddenService(String port, String privKey) {
		LOG.info("Creating v2 hidden service");
		Map<Integer, String> portLines = singletonMap(80, "127.0.0.1:" + port);
		Map<String, String> response;
		try {
			response = controlConnection.addOnion(privKey, portLines);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			return;
		}
		if (!response.containsKey(HS_ADDRESS)) {
			LOG.warning("Tor did not return a hidden service address");
			return;
		}
		String onion2 = response.get(HS_ADDRESS);
		if (LOG.isLoggable(INFO)) {
			LOG.info("V2 hidden service " + scrubOnion(onion2));
		}
		// The hostname has already been published and the private key stored
	}

	private void retireV2HiddenService() {
		LOG.info("Retiring v2 hidden service");
		TransportProperties p = new TransportProperties();
		p.put(PROP_ONION_V2, "");
		callback.mergeLocalProperties(p);
		Settings s = new Settings();
		s.put(HS_PRIVATE_KEY_V2, "");
		callback.mergeSettings(s);
	}

	private void publishV3HiddenService(String port, @Nullable String privKey) {
		LOG.info("Creating v3 hidden service");
		Map<Integer, String> portLines = singletonMap(80, "127.0.0.1:" + port);
		Map<String, String> response;
		try {
			// Use the control connection to set up the hidden service
			if (privKey == null) {
				response = controlConnection.addOnion("NEW:ED25519-V3",
						portLines, null);
			} else {
				response = controlConnection.addOnion(privKey, portLines);
			}
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
		String onion3 = response.get(HS_ADDRESS);
		if (LOG.isLoggable(INFO)) {
			LOG.info("V3 hidden service " + scrubOnion(onion3));
		}
		if (privKey == null) {
			// Publish the hidden service's onion hostname in transport props
			TransportProperties p = new TransportProperties();
			p.put(PROP_ONION_V3, onion3);
			callback.mergeLocalProperties(p);
			// Save the hidden service's private key for next time
			Settings s = new Settings();
			s.put(HS_PRIVATE_KEY_V3, response.get(HS_PRIVKEY));
			s.put(HS_V3_CREATED, String.valueOf(clock.currentTimeMillis()));
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
			File obfs4File = getObfs4ExecutableFile();
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
		wakefulIoExecutor.execute(() -> {
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
		// TODO: Remove support for v2 hidden services after a reasonable
		// migration period (migration started 2020-06-30)
		String bestOnion = null, version = null;
		String onion2 = p.get(PROP_ONION_V2);
		String onion3 = p.get(PROP_ONION_V3);
		if (!isNullOrEmpty(onion2)) {
			if (ONION_V2.matcher(onion2).matches()) {
				bestOnion = onion2;
				version = "v2";
			} else {
				// Don't scrub the address so we can find the problem
				if (LOG.isLoggable(INFO))
					LOG.info("Invalid v2 hostname: " + onion2);
			}
		}
		if (!isNullOrEmpty(onion3)) {
			if (ONION_V3.matcher(onion3).matches()) {
				bestOnion = onion3;
				version = "v3";
			} else {
				// Don't scrub the address so we can find the problem
				if (LOG.isLoggable(INFO))
					LOG.info("Invalid v3 hostname: " + onion3);
			}
		}
		if (bestOnion == null) return null;
		Socket s = null;
		try {
			if (LOG.isLoggable(INFO)) {
				LOG.info("Connecting to " + version + " "
						+ scrubOnion(bestOnion));
			}
			s = torSocketFactory.createSocket(bestOnion + ".onion", 80);
			s.setSoTimeout(socketTimeout);
			if (LOG.isLoggable(INFO)) {
				LOG.info("Connected to " + version + " "
						+ scrubOnion(bestOnion));
			}
			return new TorTransportConnection(this, s);
		} catch (IOException e) {
			if (LOG.isLoggable(INFO)) {
				LOG.info("Could not connect to " + version + " "
						+ scrubOnion(bestOnion) + ": " + e.toString());
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
		if (type.equals("HS_DESC") && msg.startsWith("UPLOADED")) {
			if (LOG.isLoggable(INFO)) {
				String[] words = msg.split(" ");
				if (words.length > 1 && ONION_V3.matcher(words[1]).matches()) {
					LOG.info("V3 descriptor uploaded");
				} else {
					LOG.info("V2 descriptor uploaded");
				}
			}
		}
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
			boolean ipv6Only = status.isIpv6Only();
			String country = locationUtils.getCurrentCountry();
			boolean blocked =
					circumventionProvider.isTorProbablyBlocked(country);
			boolean enabledByUser = settings.getBoolean(PREF_PLUGIN_ENABLE,
					DEFAULT_PREF_PLUGIN_ENABLE);
			int network = settings.getInt(PREF_TOR_NETWORK,
					DEFAULT_PREF_TOR_NETWORK);
			boolean useMobile = settings.getBoolean(PREF_TOR_MOBILE,
					DEFAULT_PREF_TOR_MOBILE);
			boolean onlyWhenCharging =
					settings.getBoolean(PREF_TOR_ONLY_WHEN_CHARGING,
							DEFAULT_PREF_TOR_ONLY_WHEN_CHARGING);
			boolean bridgesWork = circumventionProvider.doBridgesWork(country);
			boolean automatic = network == PREF_TOR_NETWORK_AUTOMATIC;

			if (LOG.isLoggable(INFO)) {
				LOG.info("Online: " + online + ", wifi: " + wifi
						+ ", IPv6 only: " + ipv6Only);
				if (country.isEmpty()) LOG.info("Country code unknown");
				else LOG.info("Country code: " + country);
				LOG.info("Charging: " + charging);
			}

			int reasonsDisabled = 0;
			boolean enableNetwork = false, enableBridges = false;
			boolean useMeek = false, enableConnectionPadding = false;

			if (!online) {
				LOG.info("Disabling network, device is offline");
			} else {
				if (!enabledByUser) {
					LOG.info("User has disabled Tor");
					reasonsDisabled |= REASON_USER;
				}
				if (!charging && onlyWhenCharging) {
					LOG.info("Configured not to use battery");
					reasonsDisabled |= REASON_BATTERY;
				}
				if (!useMobile && !wifi) {
					LOG.info("Configured not to use mobile data");
					reasonsDisabled |= REASON_MOBILE_DATA;
				}
				if (automatic && blocked && !bridgesWork) {
					LOG.info("Country is blocked");
					reasonsDisabled |= REASON_COUNTRY_BLOCKED;
				}

				if (reasonsDisabled != 0) {
					LOG.info("Disabling network due to settings");
				} else {
					LOG.info("Enabling network");
					enableNetwork = true;
					if (network == PREF_TOR_NETWORK_WITH_BRIDGES ||
							(automatic && bridgesWork)) {
						if (ipv6Only ||
								circumventionProvider.needsMeek(country)) {
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
			}

			state.setReasonsDisabled(reasonsDisabled);

			try {
				if (enableNetwork) {
					enableBridges(enableBridges, useMeek);
					enableConnectionPadding(enableConnectionPadding);
					useIpv6(ipv6Only);
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

	private void useIpv6(boolean ipv6Only) throws IOException {
		controlConnection.setConf("ClientUseIPv4", ipv6Only ? "0" : "1");
		controlConnection.setConf("ClientUseIPv6", ipv6Only ? "1" : "0");
	}

	@ThreadSafe
	@NotNullByDefault
	protected class PluginState {

		@GuardedBy("this")
		private boolean started = false,
				stopped = false,
				networkInitialised = false,
				networkEnabled = false,
				bootstrapped = false,
				circuitBuilt = false,
				settingsChecked = false;

		@GuardedBy("this")
		private int reasonsDisabled = 0;

		@GuardedBy("this")
		@Nullable
		private ServerSocket serverSocket = null;

		synchronized void setStarted() {
			started = true;
			callback.pluginStateChanged(getState());
		}

		synchronized boolean isTorRunning() {
			return started && !stopped;
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

		synchronized void setReasonsDisabled(int reasonsDisabled) {
			settingsChecked = true;
			this.reasonsDisabled = reasonsDisabled;
			callback.pluginStateChanged(getState());
		}

		// Doesn't affect getState()
		synchronized boolean setServerSocket(ServerSocket ss) {
			if (stopped || serverSocket != null) return false;
			serverSocket = ss;
			return true;
		}

		// Doesn't affect getState()
		synchronized void clearServerSocket(ServerSocket ss) {
			if (serverSocket == ss) serverSocket = null;
		}

		synchronized State getState() {
			if (!started || stopped || !settingsChecked) {
				return STARTING_STOPPING;
			}
			if (reasonsDisabled != 0) return DISABLED;
			if (!networkInitialised) return ENABLING;
			if (!networkEnabled) return INACTIVE;
			return bootstrapped && circuitBuilt ? ACTIVE : ENABLING;
		}

		synchronized int getReasonsDisabled() {
			return getState() == DISABLED ? reasonsDisabled : 0;
		}
	}
}
