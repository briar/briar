package org.briarproject.plugins.tor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.FileObserver;

import net.freehaven.tor.control.EventHandler;
import net.freehaven.tor.control.TorControlConnection;

import org.briarproject.api.Settings;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.SettingsUpdatedEvent;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.system.LocationUtils;
import org.briarproject.api.settings.SettingsManager;
import org.briarproject.util.StringUtils;

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
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import net.sourceforge.jsocks.Socks5Proxy;
import net.sourceforge.jsocks.SocksSocket;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.MODE_PRIVATE;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.net.ConnectivityManager.EXTRA_NO_CONNECTIVITY;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

class TorPlugin implements DuplexPlugin, EventHandler,
		EventListener {

	static final TransportId ID = new TransportId("tor");

	private static final String[] EVENTS = {
			"CIRC", "ORCONN", "NOTICE", "WARN", "ERR"
	};
	private static final String OWNER = "__OwningControllerProcess";
	private static final int SOCKS_PORT = 59050, CONTROL_PORT = 59051;
	private static final int COOKIE_TIMEOUT = 3000; // Milliseconds
	private static final int HOSTNAME_TIMEOUT = 30 * 1000; // Milliseconds
	private static final Pattern ONION =
			Pattern.compile("[a-z2-7]{16}\\.onion");
	private static final Logger LOG =
			Logger.getLogger(TorPlugin.class.getName());

	private final Executor ioExecutor;
	private final Context appContext;
	private final LocationUtils locationUtils;
	private final DuplexPluginCallback callback;
	private final String architecture;
	private final int maxLatency, maxIdleTime, pollingInterval, socketTimeout;
	private final File torDirectory, torFile, geoIpFile, configFile, doneFile;
	private final File cookieFile, hostnameFile;
	private final AtomicBoolean circuitBuilt;

	private volatile boolean running = false, networkEnabled = false;
	private volatile boolean bootstrapped = false;
	private volatile boolean connectedToWifi = false;
	private volatile boolean online = false;

	private volatile ServerSocket socket = null;
	private volatile Socket controlSocket = null;
	private volatile TorControlConnection controlConnection = null;
	private volatile BroadcastReceiver networkStateReceiver = null;

	TorPlugin(Executor ioExecutor, Context appContext,
			LocationUtils locationUtils, DuplexPluginCallback callback,
			String architecture, int maxLatency, int maxIdleTime,
			int pollingInterval) {
		this.ioExecutor = ioExecutor;
		this.appContext = appContext;
		this.locationUtils = locationUtils;
		this.callback = callback;
		this.architecture = architecture;
		this.maxLatency = maxLatency;
		this.maxIdleTime = maxIdleTime;
		this.pollingInterval = pollingInterval;
		if (maxIdleTime > Integer.MAX_VALUE / 2)
			socketTimeout = Integer.MAX_VALUE;
		else socketTimeout = maxIdleTime * 2;
		torDirectory = appContext.getDir("tor", MODE_PRIVATE);
		torFile = new File(torDirectory, "tor");
		geoIpFile = new File(torDirectory, "geoip");
		configFile = new File(torDirectory, "torrc");
		doneFile = new File(torDirectory, "done");
		cookieFile = new File(torDirectory, ".tor/control_auth_cookie");
		hostnameFile = new File(torDirectory, "hs/hostname");
		circuitBuilt = new AtomicBoolean(false);
	}

	public TransportId getId() {
		return ID;
	}

	public int getMaxLatency() {
		return maxLatency;
	}

	public int getMaxIdleTime() {
		return maxIdleTime;
	}

	public boolean start() throws IOException {
		// Try to connect to an existing Tor process if there is one
		boolean startProcess = false;
		try {
			controlSocket = new Socket("127.0.0.1", CONTROL_PORT);
			LOG.info("Tor is already running");
		} catch (IOException e) {
			LOG.info("Tor is not running");
			startProcess = true;
			// Install the binary, possibly overwriting an older version
			if (!installBinary()) {
				LOG.warning("Could not install Tor binary");
				return false;
			}
			// Install the GeoIP database and config file if necessary
			if (!isConfigInstalled() && !installConfig()) {
				LOG.warning("Could not install Tor config");
				return false;
			}
			LOG.info("Starting Tor");
			// Watch for the auth cookie file being created/updated
			cookieFile.getParentFile().mkdirs();
			cookieFile.createNewFile();
			CountDownLatch latch = new CountDownLatch(1);
			FileObserver obs = new WriteObserver(cookieFile, latch);
			obs.startWatching();
			// Start a new Tor process
			String torPath = torFile.getAbsolutePath();
			String configPath = configFile.getAbsolutePath();
			String pid = String.valueOf(android.os.Process.myPid());
			String[] cmd = { torPath, "-f", configPath, OWNER, pid };
			String[] env = { "HOME=" + torDirectory.getAbsolutePath() };
			Process torProcess;
			try {
				torProcess = Runtime.getRuntime().exec(cmd, env, torDirectory);
			} catch (SecurityException e1) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e1.toString(), e1);
				return false;
			}
			// Log the process's standard output until it detaches
			if (LOG.isLoggable(INFO)) {
				Scanner stdout = new Scanner(torProcess.getInputStream());
				while (stdout.hasNextLine()) LOG.info(stdout.nextLine());
				stdout.close();
			}
			try {
				// Wait for the process to detach or exit
				int exit = torProcess.waitFor();
				if (exit != 0) {
					if (LOG.isLoggable(WARNING))
						LOG.warning("Tor exited with value " + exit);
					return false;
				}
				// Wait for the auth cookie file to be created/updated
				if (!latch.await(COOKIE_TIMEOUT, MILLISECONDS)) {
					LOG.warning("Auth cookie not created");
					if (LOG.isLoggable(INFO)) listFiles(torDirectory);
					return false;
				}
			} catch (InterruptedException e1) {
				LOG.warning("Interrupted while starting Tor");
				Thread.currentThread().interrupt();
				return false;
			}
			// Now we should be able to connect to the new process
			controlSocket = new Socket("127.0.0.1", CONTROL_PORT);
		}
		running = true;
		// Open a control connection and authenticate using the cookie file
		controlConnection = new TorControlConnection(controlSocket);
		controlConnection.authenticate(read(cookieFile));
		// Tell Tor to exit when the control connection is closed
		controlConnection.takeOwnership();
		controlConnection.resetConf(Arrays.asList(OWNER));
		// Register to receive events from the Tor process
		controlConnection.setEventHandler(this);
		controlConnection.setEvents(Arrays.asList(EVENTS));
		// If Tor was already running, find out whether it's bootstrapped
		if (!startProcess) {
			String phase = controlConnection.getInfo("status/bootstrap-phase");
			if (phase != null && phase.contains("PROGRESS=100")) {
				LOG.info("Tor has already bootstrapped");
				bootstrapped = true;
			}
		}
		// Register to receive network status events
		networkStateReceiver = new NetworkStateReceiver();
		IntentFilter filter = new IntentFilter(CONNECTIVITY_ACTION);
		appContext.registerReceiver(networkStateReceiver, filter);
		// Bind a server socket to receive incoming hidden service connections
		bind();
		return true;
	}

	private boolean installBinary() {
		InputStream in = null;
		OutputStream out = null;
		try {
			// Unzip the Tor binary to the filesystem
			in = getTorInputStream();
			out = new FileOutputStream(torFile);
			copy(in, out);
			// Make the Tor binary executable
			if (!torFile.setExecutable(true, true)) {
				LOG.warning("Could not make Tor binary executable");
				return false;
			}
			return true;
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(in);
			tryToClose(out);
			return false;
		}
	}

	private boolean isConfigInstalled() {
		return geoIpFile.exists() && configFile.exists() && doneFile.exists();
	}

	private boolean installConfig() {
		InputStream in = null;
		OutputStream out = null;
		try {
			// Unzip the GeoIP database to the filesystem
			in = getGeoIpInputStream();
			out = new FileOutputStream(geoIpFile);
			copy(in, out);
			// Copy the config file to the filesystem
			in = getConfigInputStream();
			out = new FileOutputStream(configFile);
			copy(in, out);
			// Create a file to indicate that installation succeeded
			doneFile.createNewFile();
			return true;
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(in);
			tryToClose(out);
			return false;
		}
	}

	private InputStream getTorInputStream() throws IOException {
		if (LOG.isLoggable(INFO))
			LOG.info("Installing Tor binary for " + architecture);
		String filename = "tor-" + architecture + ".zip";
		InputStream in = appContext.getResources().getAssets().open(filename);
		ZipInputStream zin = new ZipInputStream(in);
		if (zin.getNextEntry() == null) throw new IOException();
		return zin;
	}

	private InputStream getGeoIpInputStream() throws IOException {
		String filename = "geoip.zip";
		InputStream in = appContext.getResources().getAssets().open(filename);
		ZipInputStream zin = new ZipInputStream(in);
		if (zin.getNextEntry() == null) throw new IOException();
		return zin;
	}

	private InputStream getConfigInputStream() throws IOException {
		return appContext.getResources().getAssets().open("torrc");
	}

	private void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[4096];
		while (true) {
			int read = in.read(buf);
			if (read == -1) break;
			out.write(buf, 0, read);
		}
		in.close();
		out.close();
	}

	private void tryToClose(InputStream in) {
		try {
			if (in != null) in.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void tryToClose(OutputStream out) {
		try {
			if (out != null) out.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void listFiles(File f) {
		if (f.isDirectory()) for (File child : f.listFiles()) listFiles(child);
		else LOG.info(f.getAbsolutePath());
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
			in.close();
		}
	}

	private void bind() {
		ioExecutor.execute(new Runnable() {
			public void run() {
				// If there's already a port number stored in config, reuse it
				String portString = callback.getSettings().get("port");
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
				s.put("port", localPort);
				callback.mergeSettings(s);
				// Create a hidden service if necessary
				ioExecutor.execute(new Runnable() {
					public void run() {
						publishHiddenService(localPort);
					}
				});
				// Accept incoming hidden service connections from Tor
				acceptContactConnections(ss);
			}
		});
	}

	private void tryToClose(ServerSocket ss) {
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
		if (!hostnameFile.exists()) {
			LOG.info("Creating hidden service");
			try {
				// Watch for the hostname file being created/updated
				File serviceDirectory = hostnameFile.getParentFile();
				serviceDirectory.mkdirs();
				hostnameFile.createNewFile();
				CountDownLatch latch = new CountDownLatch(1);
				FileObserver obs = new WriteObserver(hostnameFile, latch);
				obs.startWatching();
				// Use the control connection to update the Tor config
				List<String> config = Arrays.asList(
						"HiddenServiceDir " + serviceDirectory.getAbsolutePath(),
						"HiddenServicePort 80 127.0.0.1:" + port);
				controlConnection.setConf(config);
				controlConnection.saveConf();
				// Wait for the hostname file to be created/updated
				if (!latch.await(HOSTNAME_TIMEOUT, MILLISECONDS)) {
					LOG.warning("Hidden service not created");
					if (LOG.isLoggable(INFO)) listFiles(torDirectory);
					return;
				}
				if (!running) return;
			} catch (IOException e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			} catch (InterruptedException e) {
				LOG.warning("Interrupted while creating hidden service");
				Thread.currentThread().interrupt();
				return;
			}
		}
		// Publish the hidden service's onion hostname in transport properties
		try {
			String hostname = new String(read(hostnameFile), "UTF-8").trim();
			if (LOG.isLoggable(INFO)) LOG.info("Hidden service " + hostname);
			TransportProperties p = new TransportProperties();
			p.put("onion", hostname);
			callback.mergeLocalProperties(p);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
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
			TorTransportConnection conn = new TorTransportConnection(this, s);
			callback.incomingConnectionCreated(conn);
		}
	}

	private void enableNetwork(boolean enable) throws IOException {
		if (!running) return;
		if (LOG.isLoggable(INFO)) LOG.info("Enabling network: " + enable);
		if (!enable) {
			circuitBuilt.set(false);
			callback.transportDisabled();
		}
		networkEnabled = enable;
		controlConnection.setConf("DisableNetwork", enable ? "0" : "1");
	}

	public void stop() throws IOException {
		running = false;
		tryToClose(socket);
		if (networkStateReceiver != null)
			appContext.unregisterReceiver(networkStateReceiver);
		try {
			LOG.info("Stopping Tor");
			if (controlSocket == null)
				controlSocket = new Socket("127.0.0.1", CONTROL_PORT);
			if (controlConnection == null) {
				controlConnection = new TorControlConnection(controlSocket);
				controlConnection.authenticate(read(cookieFile));
			}
			controlConnection.setConf("DisableNetwork", "1");
			controlConnection.shutdownTor("TERM");
			controlSocket.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	public boolean isRunning() {
		return running && networkEnabled && bootstrapped && circuitBuilt.get();
	}

	public boolean shouldPoll() {
		return true;
	}

	public int getPollingInterval() {
		return pollingInterval;
	}

	public void poll(Collection<ContactId> connected) {
		if (!isRunning()) return;
		for (ContactId c : callback.getRemoteProperties().keySet())
			if (!connected.contains(c)) connectAndCallBack(c);
	}

	private void connectAndCallBack(final ContactId c) {
		ioExecutor.execute(new Runnable() {
			public void run() {
				DuplexTransportConnection d = createConnection(c);
				if (d != null) callback.outgoingConnectionCreated(c, d);
			}
		});
	}

	public DuplexTransportConnection createConnection(ContactId c) {
		if (!isRunning()) return null;
		TransportProperties p = callback.getRemoteProperties().get(c);
		if (p == null) return null;
		String onion = p.get("onion");
		if (StringUtils.isNullOrEmpty(onion)) return null;
		if (!ONION.matcher(onion).matches()) {
			if (LOG.isLoggable(INFO)) LOG.info("Invalid hostname: " + onion);
			return null;
		}
		try {
			if (LOG.isLoggable(INFO)) LOG.info("Connecting to " + onion);
			controlConnection.forgetHiddenService(onion.substring(0, 16));
			Socks5Proxy proxy = new Socks5Proxy("127.0.0.1", SOCKS_PORT);
			proxy.resolveAddrLocally(false);
			Socket s = new SocksSocket(proxy, onion, 80);
			s.setSoTimeout(socketTimeout);
			if (LOG.isLoggable(INFO)) LOG.info("Connected to " + onion);
			return new TorTransportConnection(this, s);
		} catch (IOException e) {
			if (LOG.isLoggable(INFO)) LOG.info("Could not connect to " + onion);
			return null;
		}
	}

	public boolean supportsInvitations() {
		return false;
	}

	public DuplexTransportConnection createInvitationConnection(PseudoRandom r,
			long timeout) {
		throw new UnsupportedOperationException();
	}

	public void circuitStatus(String status, String id, String path) {
		if (status.equals("BUILT") && !circuitBuilt.getAndSet(true)) {
			LOG.info("First circuit built");
			if (isRunning()) callback.transportEnabled();
		}
	}

	public void streamStatus(String status, String id, String target) {}

	public void orConnStatus(String status, String orName) {
		if (LOG.isLoggable(INFO)) LOG.info("OR connection " + status);
	}

	public void bandwidthUsed(long read, long written) {}

	public void newDescriptors(List<String> orList) {}

	public void message(String severity, String msg) {
		if (LOG.isLoggable(INFO)) LOG.info(severity + " " + msg);
		if (severity.equals("NOTICE") && msg.startsWith("Bootstrapped 100%")) {
			bootstrapped = true;
			if (isRunning()) callback.transportEnabled();
		}
	}

	public void unrecognized(String type, String msg) {}

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

	public void eventOccurred(Event e) {
		if (e instanceof SettingsUpdatedEvent) {
			// Wifi setting may have been updated
			updateConnectionStatus();
		}
	}

	private void updateConnectionStatus() {
		ioExecutor.execute(new Runnable() {
			public void run() {
				if (!running) return;

				String country = locationUtils.getCurrentCountry();
				if (LOG.isLoggable(INFO)) {
					LOG.info("Online: " + online);
					if ("".equals(country)) LOG.info("Country code unknown");
					else LOG.info("Country code: " + country);
				}
				boolean blocked = TorNetworkMetadata.isTorProbablyBlocked(
						country);
				Settings s = callback.getSettings();
				boolean wifiOnly = s.getBoolean("torOverWifi", false);

				try {
					if (!online) {
						LOG.info("Disabling network, device is offline");
						enableNetwork(false);
					} else if (blocked) {
						LOG.info("Disabling network, country is blocked");
						enableNetwork(false);
					} else if (wifiOnly & !connectedToWifi){
						LOG.info("Disabling network due to wifi setting");
						enableNetwork(false);
					} else {
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
			online = !i.getBooleanExtra(EXTRA_NO_CONNECTIVITY, false);
			// Some devices fail to set EXTRA_NO_CONNECTIVITY, double check
			Object o = ctx.getSystemService(CONNECTIVITY_SERVICE);
			ConnectivityManager cm = (ConnectivityManager) o;
			NetworkInfo net = cm.getActiveNetworkInfo();
			if (net == null || !net.isConnected()) online = false;
			connectedToWifi = (net != null && net.getType() == TYPE_WIFI
					&& net.isConnected());
			updateConnectionStatus();
		}
	}
}
