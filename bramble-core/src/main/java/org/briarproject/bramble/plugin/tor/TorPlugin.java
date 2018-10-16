package org.briarproject.bramble.plugin.tor;

import net.freehaven.tor.control.EventHandler;
import net.freehaven.tor.control.TorControlConnection;

import org.briarproject.bramble.PoliteExecutor;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.network.NetworkStatus;
import org.briarproject.bramble.api.network.event.NetworkStatusEvent;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.event.SettingsUpdatedEvent;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;
import org.briarproject.bramble.util.IoUtils;

import java.io.Closeable;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;
import javax.net.SocketFactory;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.freehaven.tor.control.TorControlCommands.HS_ADDRESS;
import static net.freehaven.tor.control.TorControlCommands.HS_PRIVKEY;
import static org.briarproject.bramble.api.plugin.TorConstants.CONTROL_PORT;
import static org.briarproject.bramble.api.plugin.TorConstants.ID;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_MOBILE;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK_AUTOMATIC;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK_NEVER;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK_WITH_BRIDGES;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_PORT;
import static org.briarproject.bramble.api.plugin.TorConstants.PROP_ONION_V2;
import static org.briarproject.bramble.api.plugin.TorConstants.PROP_ONION_V3;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.PrivacyUtils.scrubOnion;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
abstract class TorPlugin implements DuplexPlugin, EventHandler, EventListener {

	private static final Logger LOG =
			Logger.getLogger(TorPlugin.class.getName());

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
	private final Backoff backoff;
	private final DuplexPluginCallback callback;
	private final String architecture;
	private final CircumventionProvider circumventionProvider;
	private final ResourceProvider resourceProvider;
	private final int maxLatency, maxIdleTime, socketTimeout;
	private final File torDirectory, torFile, geoIpFile, configFile;
	private final File doneFile, cookieFile;
	private final ConnectionStatus connectionStatus;
	private final AtomicBoolean used = new AtomicBoolean(false);

	private volatile ServerSocket socket = null;
	private volatile Socket controlSocket = null;
	private volatile TorControlConnection controlConnection = null;
	private volatile Settings settings = null;

	protected volatile boolean running = false;

	protected abstract int getProcessId();

	protected abstract long getLastUpdateTime();

	TorPlugin(Executor ioExecutor, NetworkManager networkManager,
			LocationUtils locationUtils, SocketFactory torSocketFactory,
			Clock clock, ResourceProvider resourceProvider,
			CircumventionProvider circumventionProvider, Backoff backoff,
			DuplexPluginCallback callback, String architecture, int maxLatency,
			int maxIdleTime, File torDirectory) {
		this.ioExecutor = ioExecutor;
		this.networkManager = networkManager;
		this.locationUtils = locationUtils;
		this.torSocketFactory = torSocketFactory;
		this.clock = clock;
		this.resourceProvider = resourceProvider;
		this.circumventionProvider = circumventionProvider;
		this.backoff = backoff;
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
		configFile = new File(torDirectory, "torrc");
		doneFile = new File(torDirectory, "done");
		cookieFile = new File(torDirectory, ".tor/control_auth_cookie");
		connectionStatus = new ConnectionStatus();
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
		// Migrate old settings before having a chance to stop
		migrateSettings();
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
			controlConnection.resetConf(Collections.singletonList(OWNER));
			running = true;
			// Register to receive events from the Tor process
			controlConnection.setEventHandler(this);
			controlConnection.setEvents(Arrays.asList(EVENTS));
			// Check whether Tor has already bootstrapped
			String phase = controlConnection.getInfo("status/bootstrap-phase");
			if (phase != null && phase.contains("PROGRESS=100")) {
				LOG.info("Tor has already bootstrapped");
				connectionStatus.setBootstrapped();
			}
		} catch (IOException e) {
			throw new PluginException(e);
		}
		// Check whether we're online
		updateConnectionStatus(networkManager.getNetworkStatus());
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
			doneFile.delete();
			// Unzip the Tor binary to the filesystem
			in = getTorInputStream();
			out = new FileOutputStream(torFile);
			IoUtils.copyAndClose(in, out);
			// Make the Tor binary executable
			if (!torFile.setExecutable(true, true)) throw new IOException();
			// Unzip the GeoIP database to the filesystem
			in = getGeoIpInputStream();
			out = new FileOutputStream(geoIpFile);
			IoUtils.copyAndClose(in, out);
			// Copy the config file to the filesystem
			in = getConfigInputStream();
			out = new FileOutputStream(configFile);
			IoUtils.copyAndClose(in, out);
			doneFile.createNewFile();
		} catch (IOException e) {
			tryToClose(in);
			tryToClose(out);
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

	private InputStream getConfigInputStream() {
		return getClass().getClassLoader().getResourceAsStream("torrc");
	}

	private void tryToClose(@Nullable Closeable c) {
		try {
			if (c != null) c.close();
		} catch (IOException e) {
			logException(LOG, WARNING, e);
		}
	}

	private void tryToClose(@Nullable Socket s) {
		try {
			if (s != null) s.close();
		} catch (IOException e) {
			logException(LOG, WARNING, e);
		}
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
			tryToClose(in);
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
				tryToClose(ss);
				return;
			}
			if (!running) {
				tryToClose(ss);
				return;
			}
			socket = ss;
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

	private void tryToClose(@Nullable ServerSocket ss) {
		try {
			if (ss != null) ss.close();
		} catch (IOException e) {
			logException(LOG, WARNING, e);
		} finally {
			callback.transportDisabled();
		}
	}

	private void publishHiddenService(String port) {
		if (!running) return;
		LOG.info("Creating hidden service");
		String privKey = settings.get(HS_PRIVKEY);
		Map<Integer, String> portLines =
				Collections.singletonMap(80, "127.0.0.1:" + port);
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
		while (running) {
			Socket s;
			try {
				s = ss.accept();
				s.setSoTimeout(socketTimeout);
			} catch (IOException e) {
				// This is expected when the socket is closed
				if (LOG.isLoggable(INFO)) LOG.info(e.toString());
				return;
			}
			LOG.info("Connection received");
			backoff.reset();
			TorTransportConnection conn = new TorTransportConnection(this, s);
			callback.incomingConnectionCreated(conn);
		}
	}

	protected void enableNetwork(boolean enable) throws IOException {
		if (!running) return;
		connectionStatus.enableNetwork(enable);
		controlConnection.setConf("DisableNetwork", enable ? "0" : "1");
		if (!enable) callback.transportDisabled();
	}

	private void enableBridges(boolean enable) throws IOException {
		if (enable) {
			Collection<String> conf = new ArrayList<>();
			conf.add("UseBridges 1");
			conf.addAll(circumventionProvider.getBridges());
			controlConnection.setConf(conf);
		} else {
			controlConnection.setConf("UseBridges", "0");
		}
	}

	@Override
	public void stop() {
		running = false;
		tryToClose(socket);
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
	public boolean isRunning() {
		return running && connectionStatus.isConnected();
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
	public void poll(Map<ContactId, TransportProperties> contacts) {
		if (!isRunning()) return;
		backoff.increment();
		for (Entry<ContactId, TransportProperties> e : contacts.entrySet()) {
			connectAndCallBack(e.getKey(), e.getValue());
		}
	}

	private void connectAndCallBack(ContactId c, TransportProperties p) {
		ioExecutor.execute(() -> {
			DuplexTransportConnection d = createConnection(p);
			if (d != null) {
				backoff.reset();
				callback.outgoingConnectionCreated(c, d);
			}
		});
	}

	@Override
	public DuplexTransportConnection createConnection(TransportProperties p) {
		if (!isRunning()) return null;
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
			tryToClose(s);
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
	public void circuitStatus(String status, String id, String path) {
		if (status.equals("BUILT") &&
				connectionStatus.getAndSetCircuitBuilt()) {
			LOG.info("First circuit built");
			backoff.reset();
			if (isRunning()) callback.transportEnabled();
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
			updateConnectionStatus(networkManager.getNetworkStatus());
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
			connectionStatus.setBootstrapped();
			backoff.reset();
			if (isRunning()) callback.transportEnabled();
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
				// Works around a bug introduced in Tor 0.3.4.8. Could be
				// replaced with callback.transportDisabled() when fixed.
				disableNetwork();
				updateConnectionStatus(networkManager.getNetworkStatus());
			}
		} else if (e instanceof NetworkStatusEvent) {
			updateConnectionStatus(((NetworkStatusEvent) e).getStatus());
		}
	}

	private void disableNetwork() {
		connectionStatusExecutor.execute(() -> {
			try {
				enableNetwork(false);
			} catch (IOException ex) {
				logException(LOG, WARNING, ex);
			}
		});
	}

	private void updateConnectionStatus(NetworkStatus status) {
		connectionStatusExecutor.execute(() -> {
			if (!running) return;
			boolean online = status.isConnected();
			boolean wifi = status.isWifi();
			String country = locationUtils.getCurrentCountry();
			boolean blocked =
					circumventionProvider.isTorProbablyBlocked(country);
			int network = settings.getInt(PREF_TOR_NETWORK,
					PREF_TOR_NETWORK_AUTOMATIC);
			boolean useMobile = settings.getBoolean(PREF_TOR_MOBILE, true);
			boolean bridgesWork = circumventionProvider.doBridgesWork(country);
			boolean automatic = network == PREF_TOR_NETWORK_AUTOMATIC;

			if (LOG.isLoggable(INFO)) {
				LOG.info("Online: " + online + ", wifi: " + wifi);
				if ("".equals(country)) LOG.info("Country code unknown");
				else LOG.info("Country code: " + country);
			}

			try {
				if (!online) {
					LOG.info("Disabling network, device is offline");
					enableNetwork(false);
				} else if (network == PREF_TOR_NETWORK_NEVER ||
						(!useMobile && !wifi)) {
					LOG.info("Disabling network due to setting");
					enableNetwork(false);
				} else if (automatic && blocked && !bridgesWork) {
					LOG.info("Disabling network, country is blocked");
					enableNetwork(false);
				} else if (network == PREF_TOR_NETWORK_WITH_BRIDGES ||
						(automatic && bridgesWork)) {
					LOG.info("Enabling network, using bridges");
					enableBridges(true);
					enableNetwork(true);
				} else {
					LOG.info("Enabling network, not using bridges");
					enableBridges(false);
					enableNetwork(true);
				}
			} catch (IOException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	// TODO remove when sufficient time has passed. Added 2018-08-15
	private void migrateSettings() {
		Settings sOld = callback.getSettings();
		int oldNetwork = sOld.getInt("network", -1);
		if (oldNetwork == -1) return;
		Settings s = new Settings();
		if (oldNetwork == 0) {
			s.putInt(PREF_TOR_NETWORK, PREF_TOR_NETWORK_NEVER);
		} else if (oldNetwork == 1) {
			s.putBoolean(PREF_TOR_MOBILE, false);
		}
		s.putInt("network", -1);
		callback.mergeSettings(s);
	}

	private static class ConnectionStatus {

		// All of the following are locking: this
		private boolean networkEnabled = false;
		private boolean bootstrapped = false, circuitBuilt = false;

		private synchronized void setBootstrapped() {
			bootstrapped = true;
		}

		private synchronized boolean getAndSetCircuitBuilt() {
			boolean firstCircuit = !circuitBuilt;
			circuitBuilt = true;
			return firstCircuit;
		}

		private synchronized void enableNetwork(boolean enable) {
			networkEnabled = enable;
			if (!enable) circuitBuilt = false;
		}

		private synchronized boolean isConnected() {
			return networkEnabled && bootstrapped && circuitBuilt;
		}
	}
}
