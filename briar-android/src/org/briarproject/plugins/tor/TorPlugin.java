package org.briarproject.plugins.tor;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.MODE_PRIVATE;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.net.ConnectivityManager.EXTRA_NO_CONNECTIVITY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

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
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import net.freehaven.tor.control.EventHandler;
import net.freehaven.tor.control.TorControlConnection;

import org.briarproject.api.ContactId;
import org.briarproject.api.TransportConfig;
import org.briarproject.api.TransportId;
import org.briarproject.api.TransportProperties;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.lifecycle.ShutdownManager;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.system.LocationUtils;
import org.briarproject.util.StringUtils;

import socks.Socks5Proxy;
import socks.SocksSocket;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.FileObserver;

class TorPlugin implements DuplexPlugin, EventHandler {

	static final TransportId ID = new TransportId("tor");

	private static final String[] EVENTS = { 
		"CIRC", "STREAM", "ORCONN", "NOTICE", "WARN", "ERR"
	};
	private static final int SOCKS_PORT = 59050, CONTROL_PORT = 59051;
	private static final int COOKIE_TIMEOUT = 3000; // Milliseconds
	private static final int HOSTNAME_TIMEOUT = 30 * 1000; // Milliseconds
	private static final Pattern ONION =
			Pattern.compile("[a-z2-7]{16}\\.onion");
	private static final Logger LOG =
			Logger.getLogger(TorPlugin.class.getName());

	private final Executor pluginExecutor;
	private final Context appContext;
	private final LocationUtils locationUtils;
	private final ShutdownManager shutdownManager;
	private final DuplexPluginCallback callback;
	private final int maxFrameLength;
	private final long maxLatency, pollingInterval;
	private final File torDirectory, torFile, geoIpFile, configFile, doneFile;
	private final File cookieFile, pidFile, hostnameFile;

	private volatile boolean running = false, networkEnabled = false;
	private volatile Process tor = null;
	private volatile int pid = -1;
	private volatile ServerSocket socket = null;
	private volatile Socket controlSocket = null;
	private volatile TorControlConnection controlConnection = null;
	private volatile BroadcastReceiver networkStateReceiver = null;

	TorPlugin(Executor pluginExecutor, Context appContext,
			LocationUtils locationUtils, ShutdownManager shutdownManager,
			DuplexPluginCallback callback, int maxFrameLength, long maxLatency,
			long pollingInterval) {
		this.pluginExecutor = pluginExecutor;
		this.appContext = appContext;
		this.locationUtils = locationUtils;
		this.shutdownManager = shutdownManager;
		this.callback = callback;
		this.maxFrameLength = maxFrameLength;
		this.maxLatency = maxLatency;
		this.pollingInterval = pollingInterval;
		torDirectory = appContext.getDir("tor", MODE_PRIVATE);
		torFile = new File(torDirectory, "tor");
		geoIpFile = new File(torDirectory, "geoip");
		configFile = new File(torDirectory, "torrc");
		doneFile = new File(torDirectory, "done");
		cookieFile = new File(torDirectory, ".tor/control_auth_cookie");
		pidFile = new File(torDirectory, ".tor/pid");
		hostnameFile = new File(torDirectory, "hostname");
	}

	public TransportId getId() {
		return ID;
	}

	public int getMaxFrameLength() {
		return maxFrameLength;
	}

	public long getMaxLatency() {
		return maxLatency;
	}

	public boolean start() throws IOException {
		// Try to connect to an existing Tor process if there is one
		boolean startProcess = false;
		try {
			controlSocket = new Socket("127.0.0.1", CONTROL_PORT);
			LOG.info("Tor is already running");
			if(readPidFile() == -1) {
				controlSocket.close();
				killZombieProcess();
				startProcess = true;
			}
		} catch(IOException e) {
			LOG.info("Tor is not running");
			startProcess = true;
		}
		if(startProcess) {
			// Install the binary, GeoIP database and config file if necessary
			if(!isInstalled() && !install()) {
				LOG.info("Could not install Tor");
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
			String[] cmd = { torPath, "-f", configPath };
			String[] env = { "HOME=" + torDirectory.getAbsolutePath() };
			try {
				tor = Runtime.getRuntime().exec(cmd, env, torDirectory);
			} catch(SecurityException e1) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e1.toString(), e1);
				return false;
			}
			// Log the process's standard output until it detaches
			if(LOG.isLoggable(INFO)) {
				Scanner stdout = new Scanner(tor.getInputStream());
				while(stdout.hasNextLine()) LOG.info(stdout.nextLine());
				stdout.close();
			}
			try {
				// Wait for the process to detach or exit
				int exit = tor.waitFor();
				if(exit != 0) {
					if(LOG.isLoggable(WARNING))
						LOG.warning("Tor exited with value " + exit);
					return false;
				}
				// Wait for the auth cookie file to be created/updated
				if(!latch.await(COOKIE_TIMEOUT, MILLISECONDS)) {
					LOG.warning("Auth cookie not created");
					listFiles(torDirectory);
					return false;
				}
			} catch(InterruptedException e1) {
				LOG.warning("Interrupted while starting Tor");
				Thread.currentThread().interrupt();
				return false;
			}
			// Now we should be able to connect to the new process
			controlSocket = new Socket("127.0.0.1", CONTROL_PORT);
		}
		running = true;
		// Read the PID of the Tor process so we can kill it if necessary
		pid = readPidFile();
		// Create a shutdown hook to ensure the Tor process is killed
		shutdownManager.addShutdownHook(new Runnable() {
			public void run() {
				killTorProcess();
				killZombieProcess();
			}
		});
		// Open a control connection and authenticate using the cookie file
		controlConnection = new TorControlConnection(controlSocket);
		controlConnection.authenticate(read(cookieFile));
		// Register to receive events from the Tor process
		controlConnection.setEventHandler(this);
		controlConnection.setEvents(Arrays.asList(EVENTS));
		// Register to receive network status events
		networkStateReceiver = new NetworkStateReceiver();
		IntentFilter filter = new IntentFilter(CONNECTIVITY_ACTION);
		appContext.registerReceiver(networkStateReceiver, filter);
		// Bind a server socket to receive incoming hidden service connections
		pluginExecutor.execute(new Runnable() {
			public void run() {
				bind();
			}
		});
		return true;
	}

	private boolean isInstalled() {
		return doneFile.exists();
	}

	private boolean install() {
		InputStream in = null;
		OutputStream out = null;
		try {
			// Unzip the Tor binary to the filesystem
			in = getTorInputStream();
			out = new FileOutputStream(torFile);
			copy(in, out);
			// Unzip the GeoIP database to the filesystem
			in = getGeoIpInputStream();
			out = new FileOutputStream(geoIpFile);
			copy(in, out);
			// Copy the config file to the filesystem
			in = getConfigInputStream();
			out = new FileOutputStream(configFile);
			copy(in, out);
			// Make the Tor binary executable
			if(!setExecutable(torFile)) {
				LOG.warning("Could not make Tor executable");
				return false;
			}
			// Create a file to indicate that installation succeeded
			doneFile.createNewFile();
			return true;
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(in);
			tryToClose(out);
			return false;
		}
	}

	private InputStream getTorInputStream() throws IOException {
		InputStream in = appContext.getResources().getAssets().open("tor");
		ZipInputStream zin = new ZipInputStream(in);
		if(zin.getNextEntry() == null) throw new IOException();
		return zin;
	}

	private InputStream getGeoIpInputStream() throws IOException {
		InputStream in = appContext.getResources().getAssets().open("geoip");
		ZipInputStream zin = new ZipInputStream(in);
		if(zin.getNextEntry() == null) throw new IOException();
		return zin;
	}

	private InputStream getConfigInputStream() throws IOException {
		return appContext.getResources().getAssets().open("torrc");
	}

	private void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[4096];
		while(true) {
			int read = in.read(buf);
			if(read == -1) break;
			out.write(buf, 0, read);
		}
		in.close();
		out.close();
	}

	@SuppressLint("NewApi")
	private boolean setExecutable(File f) {
		if(Build.VERSION.SDK_INT >= 9) {
			return f.setExecutable(true, true);
		} else {
			String[] command = { "chmod", "700", f.getAbsolutePath() };
			try {
				return Runtime.getRuntime().exec(command).waitFor() == 0;
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			} catch(InterruptedException e) {
				LOG.warning("Interrupted while executing chmod");
				Thread.currentThread().interrupt();
			} catch(SecurityException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
			return false;
		}
	}

	private void tryToClose(InputStream in) {
		try {
			if(in != null) in.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void tryToClose(OutputStream out) {
		try {
			if(out != null) out.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void listFiles(File f) {
		if(f.isDirectory()) for(File f1 : f.listFiles()) listFiles(f1);
		else if(LOG.isLoggable(INFO)) LOG.info(f.getAbsolutePath());
	}

	private byte[] read(File f) throws IOException {
		byte[] b = new byte[(int) f.length()];
		FileInputStream in = new FileInputStream(f);
		try {
			int offset = 0;
			while(offset < b.length) {
				int read = in.read(b, offset, b.length - offset);
				if(read == -1) throw new EOFException();
				offset += read;
			}
			return b;
		} finally {
			in.close();
		}
	}

	private int readPidFile() {
		// Read the PID of the Tor process so we can kill it if necessary
		try {
			return Integer.parseInt(new String(read(pidFile), "UTF-8").trim());
		} catch(IOException e) {
			LOG.warning("Could not read PID file");
		} catch(NumberFormatException e) {
			LOG.warning("Could not parse PID file");
		}
		return -1;
	}

	/*
	 * If the app crashes, leaving a Tor process running, and the user clears
	 * the app's data, removing the PID file and auth cookie file, it's no
	 * longer possible to communicate with the zombie process and it must be
	 * killed. ActivityManager.killBackgroundProcesses() doesn't seem to work
	 * in this case, so we must parse the output of ps to get the PID.
	 * <p>
	 * On all tested devices, the output consists of a header line followed by
	 * one line per process. The second column is the PID and the last column
	 * is the process name, which includes the app's package name.
	 */
	private void killZombieProcess() {
		String packageName = "/" + appContext.getPackageName() + "/";
		try {
			// Parse the output of ps
			Process ps = Runtime.getRuntime().exec("ps");
			Scanner scanner = new Scanner(ps.getInputStream());
			// Discard the header line
			if(scanner.hasNextLine()) scanner.nextLine();
			// Look for a Tor process with our package name
			boolean found = false;
			while(scanner.hasNextLine()) {
				String[] columns = scanner.nextLine().split("\\s+");
				if(columns.length < 3) break;
				int pid = Integer.parseInt(columns[1]);
				String name = columns[columns.length - 1];
				if(name.contains(packageName) && name.endsWith("/tor")) {
					if(LOG.isLoggable(INFO))
						LOG.info("Killing zombie process " + pid);
					android.os.Process.killProcess(pid);
					found = true;
				}
			}
			if(!found) LOG.info("No zombies found");
			scanner.close();
		} catch(IOException e) {
			LOG.warning("Could not parse ps output");
		} catch(SecurityException e) {
			LOG.warning("Could not execute ps");
		}
	}

	private void killTorProcess() {
		if(tor != null) {
			LOG.info("Killing Tor via destroy()");
			tor.destroy();
		}
		if(pid != -1) {
			if(LOG.isLoggable(INFO))
				LOG.info("Killing Tor via killProcess(" + pid + ")");
			android.os.Process.killProcess(pid);
		}
	}

	private void bind() {
		// If there's already a port number stored in config, reuse it
		String portString = callback.getConfig().get("port");
		int port;
		if(StringUtils.isNullOrEmpty(portString)) port = 0;
		else port = Integer.parseInt(portString);
		// Bind a server socket to receive connections from the Tor process
		ServerSocket ss = null;
		try {
			ss = new ServerSocket();
			ss.bind(new InetSocketAddress("127.0.0.1", port));
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			tryToClose(ss);
		}
		if(!running) {
			tryToClose(ss);
			return;
		}
		socket = ss;
		// Store the port number
		final String localPort = String.valueOf(ss.getLocalPort());
		TransportConfig c  = new TransportConfig();
		c.put("port", localPort);
		callback.mergeConfig(c);
		// Create a hidden service if necessary
		pluginExecutor.execute(new Runnable() {
			public void run() {
				publishHiddenService(localPort);
			}
		});
		// Accept incoming hidden service connections from the Tor process
		acceptContactConnections(ss);
	}

	private void tryToClose(ServerSocket ss) {
		try {
			if(ss != null) ss.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void publishHiddenService(final String port) {
		if(!running) return;
		if(!hostnameFile.exists()) {
			LOG.info("Creating hidden service");
			try {
				// Watch for the hostname file being created/updated
				hostnameFile.getParentFile().mkdirs();
				hostnameFile.createNewFile();
				CountDownLatch latch = new CountDownLatch(1);
				FileObserver obs = new WriteObserver(hostnameFile, latch);
				obs.startWatching();
				// Use the control connection to update the Tor config
				List<String> config = Arrays.asList(
						"HiddenServiceDir " + torDirectory.getAbsolutePath(),
						"HiddenServicePort 80 127.0.0.1:" + port);
				controlConnection.setConf(config);
				controlConnection.saveConf();
				// Wait for the hostname file to be created/updated
				if(!latch.await(HOSTNAME_TIMEOUT, MILLISECONDS)) {
					LOG.warning("Hidden service not created");
					listFiles(torDirectory);
					return;
				}
				if(!running) return;
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			} catch(InterruptedException e) {
				LOG.warning("Interrupted while creating hidden service");
				Thread.currentThread().interrupt();
				return;
			}
		}
		// Publish the hidden service's onion hostname in transport properties
		try {
			String hostname = new String(read(hostnameFile), "UTF-8").trim();
			if(LOG.isLoggable(INFO)) LOG.info("Hidden service " + hostname);
			TransportProperties p = new TransportProperties();
			p.put("onion", hostname);
			callback.mergeLocalProperties(p);
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void acceptContactConnections(ServerSocket ss) {
		while(running) {
			Socket s;
			try {
				s = ss.accept();
			} catch(IOException e) {
				// This is expected when the socket is closed
				if(LOG.isLoggable(INFO)) LOG.info(e.toString());
				tryToClose(ss);
				return;
			}
			LOG.info("Connection received");
			TorTransportConnection conn = new TorTransportConnection(this, s);
			callback.incomingConnectionCreated(conn);
		}
	}

	private void enableNetwork(boolean enable) throws IOException {
		if(!running) return;
		if(LOG.isLoggable(INFO)) LOG.info("Enabling network: " + enable);
		controlConnection.setConf("DisableNetwork", enable ? "0" : "1");
		networkEnabled = enable;
	}

	public void stop() throws IOException {
		running = false;
		tryToClose(socket);
		if(networkStateReceiver != null)
			appContext.unregisterReceiver(networkStateReceiver);
		try {
			LOG.info("Stopping Tor");
			if(controlSocket == null)
				controlSocket = new Socket("127.0.0.1", CONTROL_PORT);
			if(controlConnection == null) {
				controlConnection = new TorControlConnection(controlSocket);
				controlConnection.authenticate(read(cookieFile));
			}
			controlConnection.setConf("DisableNetwork", "1");
			controlConnection.shutdownTor("TERM");
			controlSocket.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			killTorProcess();
			killZombieProcess();
		}
	}

	public boolean isRunning() {
		return running && networkEnabled;
	}

	public boolean shouldPoll() {
		return true;
	}

	public long getPollingInterval() {
		return pollingInterval;
	}

	public void poll(Collection<ContactId> connected) {
		if(!isRunning()) return;
		Map<ContactId, TransportProperties> remote =
				callback.getRemoteProperties();
		for(final ContactId c : remote.keySet()) {
			if(connected.contains(c)) continue;
			pluginExecutor.execute(new Runnable() {
				public void run() {
					connectAndCallBack(c);
				}
			});
		}
	}

	private void connectAndCallBack(ContactId c) {
		DuplexTransportConnection d = createConnection(c);
		if(d != null) callback.outgoingConnectionCreated(c, d);
	}

	public DuplexTransportConnection createConnection(ContactId c) {
		if(!isRunning()) return null;
		TransportProperties p = callback.getRemoteProperties().get(c);
		if(p == null) return null;
		String onion = p.get("onion");
		if(StringUtils.isNullOrEmpty(onion)) return null;
		if(!ONION.matcher(onion).matches()) {
			if(LOG.isLoggable(INFO)) LOG.info("Invalid hostname: " + onion);
			return null;
		}
		try {
			if(LOG.isLoggable(INFO)) LOG.info("Connecting to " + onion);
			Socks5Proxy proxy = new Socks5Proxy("127.0.0.1", SOCKS_PORT);
			proxy.resolveAddrLocally(false);
			Socket s = new SocksSocket(proxy, onion, 80);
			if(LOG.isLoggable(INFO)) LOG.info("Connected to " + onion);
			return new TorTransportConnection(this, s);
		} catch(IOException e) {
			if(LOG.isLoggable(INFO)) LOG.info("Could not connect to " + onion);
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
		if(LOG.isLoggable(INFO)) {
			if(!"EXTENDED".equals(status))
				LOG.info("Circuit " + id + " " + status);
		}
	}

	public void streamStatus(String status, String id, String target) {
		if(LOG.isLoggable(INFO)) LOG.info("Stream " + id + " " + status);
	}

	public void orConnStatus(String status, String orName) {
		if(LOG.isLoggable(INFO)) LOG.info("OR connection " + status);
	}

	public void bandwidthUsed(long read, long written) {}

	public void newDescriptors(List<String> orList) {}

	public void message(String severity, String msg) {
		if(LOG.isLoggable(INFO)) LOG.info(severity + " " + msg);		
	}

	public void unrecognized(String type, String msg) {}

	private static class WriteObserver extends FileObserver {

		private final CountDownLatch latch;

		private WriteObserver(File file, CountDownLatch latch) {
			super(file.getAbsolutePath(), CLOSE_WRITE);
			this.latch = latch;
		}

		public void onEvent(int event, String path) {
			stopWatching();
			latch.countDown();
		}
	}

	private class NetworkStateReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context ctx, Intent i) {
			if(!running) return;
			boolean online = !i.getBooleanExtra(EXTRA_NO_CONNECTIVITY, false);
			if(online) {
				// Some devices fail to set EXTRA_NO_CONNECTIVITY, double check
				Object o = ctx.getSystemService(CONNECTIVITY_SERVICE);
				ConnectivityManager cm = (ConnectivityManager) o;
				NetworkInfo net = cm.getActiveNetworkInfo();
				if(net == null || !net.isConnected()) online = false;
			}
			String country = locationUtils.getCurrentCountry();
			if(LOG.isLoggable(INFO)) {
				LOG.info("Online: " + online);
				if("".equals(country)) LOG.info("Country code unknown");
				else LOG.info("Country code: " + country);
			}
			boolean blocked = TorNetworkMetadata.isTorProbablyBlocked(country);
			try {
				enableNetwork(online && !blocked);
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}
}
