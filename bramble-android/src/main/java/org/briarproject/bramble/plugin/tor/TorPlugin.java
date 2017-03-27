package org.briarproject.bramble.plugin.tor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.FileObserver;
import android.os.PowerManager;

import net.freehaven.tor.control.EventHandler;
import net.freehaven.tor.control.TorControlConnection;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.PseudoRandom;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.keyagreement.KeyAgreementListener;
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
import org.briarproject.bramble.api.reporting.DevReporter;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.event.SettingsUpdatedEvent;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.util.AndroidUtils;
import org.briarproject.bramble.util.IoUtils;
import org.briarproject.bramble.util.StringUtils;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;
import javax.net.SocketFactory;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.MODE_PRIVATE;
import static android.content.Context.POWER_SERVICE;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.os.PowerManager.PARTIAL_WAKE_LOCK;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static net.freehaven.tor.control.TorControlCommands.HS_ADDRESS;
import static net.freehaven.tor.control.TorControlCommands.HS_PRIVKEY;
import static org.briarproject.bramble.api.plugin.TorConstants.CONTROL_PORT;
import static org.briarproject.bramble.api.plugin.TorConstants.ID;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK_ALWAYS;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK_NEVER;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_NETWORK_WIFI;
import static org.briarproject.bramble.api.plugin.TorConstants.PREF_TOR_PORT;
import static org.briarproject.bramble.util.PrivacyUtils.scrubOnion;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class TorPlugin implements DuplexPlugin, EventHandler, EventListener {

	private static final String PROP_ONION = "onion";
	private static final String[] EVENTS = {
			"CIRC", "ORCONN", "HS_DESC", "NOTICE", "WARN", "ERR"
	};
	private static final String OWNER = "__OwningControllerProcess";
	private static final int COOKIE_TIMEOUT = 3000; // Milliseconds
	private static final Pattern ONION = Pattern.compile("[a-z2-7]{16}");
	private static final Logger LOG =
			Logger.getLogger(TorPlugin.class.getName());

	private final Executor ioExecutor;
	private final Context appContext;
	private final LocationUtils locationUtils;
	private final DevReporter reporter;
	private final SocketFactory torSocketFactory;
	private final Backoff backoff;
	private final DuplexPluginCallback callback;
	private final String architecture;
	private final int maxLatency, maxIdleTime, socketTimeout;
	private final ConnectionStatus connectionStatus;
	private final File torDirectory, torFile, geoIpFile, configFile;
	private final File doneFile, cookieFile;
	private final PowerManager.WakeLock wakeLock;
	private final AtomicBoolean used = new AtomicBoolean(false);

	private volatile boolean running = false;
	private volatile ServerSocket socket = null;
	private volatile Socket controlSocket = null;
	private volatile TorControlConnection controlConnection = null;
	private volatile BroadcastReceiver networkStateReceiver = null;

	TorPlugin(Executor ioExecutor, Context appContext,
			LocationUtils locationUtils, DevReporter reporter,
			SocketFactory torSocketFactory, Backoff backoff,
			DuplexPluginCallback callback, String architecture, int maxLatency,
			int maxIdleTime) {
		this.ioExecutor = ioExecutor;
		this.appContext = appContext;
		this.locationUtils = locationUtils;
		this.reporter = reporter;
		this.torSocketFactory = torSocketFactory;
		this.backoff = backoff;
		this.callback = callback;
		this.architecture = architecture;
		this.maxLatency = maxLatency;
		this.maxIdleTime = maxIdleTime;
		if (maxIdleTime > Integer.MAX_VALUE / 2)
			socketTimeout = Integer.MAX_VALUE;
		else socketTimeout = maxIdleTime * 2;
		connectionStatus = new ConnectionStatus();
		torDirectory = appContext.getDir("tor", MODE_PRIVATE);
		torFile = new File(torDirectory, "tor");
		geoIpFile = new File(torDirectory, "geoip");
		configFile = new File(torDirectory, "torrc");
		doneFile = new File(torDirectory, "done");
		cookieFile = new File(torDirectory, ".tor/control_auth_cookie");
		Object o = appContext.getSystemService(POWER_SERVICE);
		PowerManager pm = (PowerManager) o;
		wakeLock = pm.newWakeLock(PARTIAL_WAKE_LOCK, "TorPlugin");
		wakeLock.setReferenceCounted(false);
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
		// Install or update the assets if necessary
		if (!assetsAreUpToDate()) installAssets();
		LOG.info("Starting Tor");
		// Watch for the auth cookie file being updated
		try {
			cookieFile.getParentFile().mkdirs();
			cookieFile.createNewFile();
		} catch (IOException e) {
			throw new PluginException(e);
		}
		CountDownLatch latch = new CountDownLatch(1);
		FileObserver obs = new WriteObserver(cookieFile, latch);
		obs.startWatching();
		// Start a new Tor process
		String torPath = torFile.getAbsolutePath();
		String configPath = configFile.getAbsolutePath();
		String pid = String.valueOf(android.os.Process.myPid());
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
			while (stdout.hasNextLine() || stderr.hasNextLine()){
				if(stdout.hasNextLine()) {
					LOG.info(stdout.nextLine());
				}
				if(stderr.hasNextLine()){
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
			if (!latch.await(COOKIE_TIMEOUT, MILLISECONDS)) {
				LOG.warning("Auth cookie not created");
				if (LOG.isLoggable(INFO)) listFiles(torDirectory);
				throw new PluginException();
			}
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
		// Register to receive network status events
		networkStateReceiver = new NetworkStateReceiver();
		IntentFilter filter = new IntentFilter(CONNECTIVITY_ACTION);
		appContext.registerReceiver(networkStateReceiver, filter);
		// Bind a server socket to receive incoming hidden service connections
		bind();
	}

	private boolean assetsAreUpToDate() {
		try {
			PackageManager pm = appContext.getPackageManager();
			PackageInfo pi = pm.getPackageInfo(appContext.getPackageName(), 0);
			return doneFile.lastModified() > pi.lastUpdateTime;
		} catch (NameNotFoundException e) {
			throw new RuntimeException(e);
		}
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
		int resId = getResourceId("tor_" + architecture);
		InputStream in = appContext.getResources().openRawResource(resId);
		ZipInputStream zin = new ZipInputStream(in);
		if (zin.getNextEntry() == null) throw new IOException();
		return zin;
	}

	private InputStream getGeoIpInputStream() throws IOException {
		int resId = getResourceId("geoip");
		InputStream in = appContext.getResources().openRawResource(resId);
		ZipInputStream zin = new ZipInputStream(in);
		if (zin.getNextEntry() == null) throw new IOException();
		return zin;
	}

	private InputStream getConfigInputStream() throws IOException {
		int resId = getResourceId("torrc");
		return appContext.getResources().openRawResource(resId);
	}

	private int getResourceId(String filename) {
		Resources res = appContext.getResources();
		return res.getIdentifier(filename, "raw", appContext.getPackageName());
	}

	private void tryToClose(@Nullable Closeable c) {
		try {
			if (c != null) c.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void tryToClose(@Nullable Socket s) {
		try {
			if (s != null) s.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void listFiles(File f) {
		if (f.isDirectory()) {
			File[] children = f.listFiles();
			if (children != null) for (File child : children) listFiles(child);
		} else {
			LOG.info(f.getAbsolutePath());
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

	private void sendDevReports() {
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				// TODO: Trigger this with a TransportEnabledEvent
				File reportDir = AndroidUtils.getReportDir(appContext);
				reporter.sendReports(reportDir);
			}
		});
	}

	private void bind() {
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				// If there's already a port number stored in config, reuse it
				String portString = callback.getSettings().get(PREF_TOR_PORT);
				int port;
				if (StringUtils.isNullOrEmpty(portString)) port = 0;
				else port = Integer.parseInt(portString);
				// Bind a server socket to receive connections from Tor
				ServerSocket ss = null;
				try {
					ss = new ServerSocket();
					ss.bind(new InetSocketAddress("127.0.0.1", port));
				} catch (IOException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
					tryToClose(ss);
					return;
				}
				if (!running) {
					tryToClose(ss);
					return;
				}
				socket = ss;
				// Store the port number
				final String localPort = String.valueOf(ss.getLocalPort());
				Settings s = new Settings();
				s.put(PREF_TOR_PORT, localPort);
				callback.mergeSettings(s);
				// Create a hidden service if necessary
				ioExecutor.execute(new Runnable() {
					@Override
					public void run() {
						publishHiddenService(localPort);
					}
				});
				backoff.reset();
				// Accept incoming hidden service connections from Tor
				acceptContactConnections(ss);
			}
		});
	}

	private void tryToClose(@Nullable ServerSocket ss) {
		try {
			if (ss != null) ss.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} finally {
			callback.transportDisabled();
		}
	}

	private void publishHiddenService(String port) {
		if (!running) return;
		LOG.info("Creating hidden service");
		String privKey = callback.getSettings().get(HS_PRIVKEY);
		Map<Integer, String> portLines =
				Collections.singletonMap(80, "127.0.0.1:" + port);
		Map<String, String> response;
		try {
			// Use the control connection to set up the hidden service
			if (privKey == null)
				response = controlConnection.addOnion(portLines);
			else response = controlConnection.addOnion(privKey, portLines);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
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
		String hostname = response.get(HS_ADDRESS);
		if (LOG.isLoggable(INFO))
			LOG.info("Hidden service " + scrubOnion(hostname));
		TransportProperties p = new TransportProperties();
		p.put(PROP_ONION, hostname);
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

	private void enableNetwork(boolean enable) throws IOException {
		if (!running) return;
		if (enable) wakeLock.acquire();
		connectionStatus.enableNetwork(enable);
		controlConnection.setConf("DisableNetwork", enable ? "0" : "1");
		if (!enable) {
			callback.transportDisabled();
			wakeLock.release();
		}
	}

	@Override
	public void stop() throws PluginException {
		running = false;
		tryToClose(socket);
		if (networkStateReceiver != null)
			appContext.unregisterReceiver(networkStateReceiver);
		if (controlSocket != null && controlConnection != null) {
			try {
				LOG.info("Stopping Tor");
				controlConnection.setConf("DisableNetwork", "1");
				controlConnection.shutdownTor("TERM");
				controlSocket.close();
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
		wakeLock.release();
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
	public void poll(Collection<ContactId> connected) {
		if (!isRunning()) return;
		backoff.increment();
		// TODO: Pass properties to connectAndCallBack()
		for (ContactId c : callback.getRemoteProperties().keySet())
			if (!connected.contains(c)) connectAndCallBack(c);
	}

	private void connectAndCallBack(final ContactId c) {
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				DuplexTransportConnection d = createConnection(c);
				if (d != null) {
					backoff.reset();
					callback.outgoingConnectionCreated(c, d);
				}
			}
		});
	}

	@Override
	public DuplexTransportConnection createConnection(ContactId c) {
		if (!isRunning()) return null;
		TransportProperties p = callback.getRemoteProperties().get(c);
		if (p == null) return null;
		String onion = p.get(PROP_ONION);
		if (StringUtils.isNullOrEmpty(onion)) return null;
		if (!ONION.matcher(onion).matches()) {
			// not scrubbing this address, so we are able to find the problem
			if (LOG.isLoggable(INFO)) LOG.info("Invalid hostname: " + onion);
			return null;
		}
		Socket s = null;
		try {
			if (LOG.isLoggable(INFO))
				LOG.info("Connecting to " + scrubOnion(onion));
			controlConnection.forgetHiddenService(onion);
			s = torSocketFactory.createSocket(onion + ".onion", 80);
			s.setSoTimeout(socketTimeout);
			if (LOG.isLoggable(INFO))
				LOG.info("Connected to " + scrubOnion(onion));
			return new TorTransportConnection(this, s);
		} catch (IOException e) {
			if (LOG.isLoggable(INFO)) {
				LOG.info("Could not connect to " + scrubOnion(onion) + ": " +
						e.toString());
			}
			tryToClose(s);
			return null;
		}
	}

	@Override
	public boolean supportsInvitations() {
		return false;
	}

	@Override
	public DuplexTransportConnection createInvitationConnection(PseudoRandom r,
			long timeout, boolean alice) {
		throw new UnsupportedOperationException();
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
			byte[] commitment, BdfList descriptor, long timeout) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void circuitStatus(String status, String id, String path) {
		if (status.equals("BUILT") &&
				connectionStatus.getAndSetCircuitBuilt()) {
			LOG.info("First circuit built");
			backoff.reset();
			if (isRunning()) {
				sendDevReports();
				callback.transportEnabled();
			}
		}
	}

	@Override
	public void streamStatus(String status, String id, String target) {
	}

	@Override
	public void orConnStatus(String status, String orName) {
		if (LOG.isLoggable(INFO)) LOG.info("OR connection " + status);
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
			if (isRunning()) {
				sendDevReports();
				callback.transportEnabled();
			}
		}
	}

	@Override
	public void unrecognized(String type, String msg) {
		if (type.equals("HS_DESC") && msg.startsWith("UPLOADED"))
			LOG.info("Descriptor uploaded");
	}

	private static class WriteObserver extends FileObserver {

		private final CountDownLatch latch;

		private WriteObserver(File file, CountDownLatch latch) {
			super(file.getAbsolutePath(), CLOSE_WRITE);
			this.latch = latch;
		}

		@Override
		public void onEvent(int event, String path) {
			stopWatching();
			latch.countDown();
		}
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof SettingsUpdatedEvent) {
			SettingsUpdatedEvent s = (SettingsUpdatedEvent) e;
			if (s.getNamespace().equals(ID.getString())) {
				LOG.info("Tor settings updated");
				updateConnectionStatus();
			}
		}
	}

	private void updateConnectionStatus() {
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				if (!running) return;

				Object o = appContext.getSystemService(CONNECTIVITY_SERVICE);
				ConnectivityManager cm = (ConnectivityManager) o;
				NetworkInfo net = cm.getActiveNetworkInfo();
				boolean online = net != null && net.isConnected();
				boolean wifi = online && net.getType() == TYPE_WIFI;
				String country = locationUtils.getCurrentCountry();
				boolean blocked = TorNetworkMetadata.isTorProbablyBlocked(
						country);
				Settings s = callback.getSettings();
				int network = s.getInt(PREF_TOR_NETWORK,
						PREF_TOR_NETWORK_ALWAYS);

				if (LOG.isLoggable(INFO)) {
					LOG.info("Online: " + online + ", wifi: " + wifi);
					if ("".equals(country)) LOG.info("Country code unknown");
					else LOG.info("Country code: " + country);
				}

				try {
					if (!online) {
						LOG.info("Disabling network, device is offline");
						enableNetwork(false);
					} else if (blocked) {
						LOG.info("Disabling network, country is blocked");
						enableNetwork(false);
					} else if (network == PREF_TOR_NETWORK_NEVER
							|| (network == PREF_TOR_NETWORK_WIFI && !wifi)) {
						LOG.info("Disabling network due to data setting");
						enableNetwork(false);
					} else {
						LOG.info("Enabling network");
						enableNetwork(true);
					}
				} catch (IOException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private class NetworkStateReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context ctx, Intent i) {
			if (!running) return;
			if (CONNECTIVITY_ACTION.equals(i.getAction())) {
				LOG.info("Detected connectivity change");
				updateConnectionStatus();
			}
		}
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
			circuitBuilt = false;
		}

		private synchronized boolean isConnected() {
			return networkEnabled && bootstrapped && circuitBuilt;
		}
	}
}
