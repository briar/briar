package net.sf.briar.plugins.tor;

import static android.content.Context.MODE_PRIVATE;
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
import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.util.StringUtils;
import socks.Socks5Proxy;
import socks.SocksSocket;
import android.content.Context;
import android.os.Build;
import android.os.FileObserver;

class TorPlugin implements DuplexPlugin, EventHandler {

	static final byte[] TRANSPORT_ID =
			StringUtils.fromHexString("fa866296495c73a52e6a82fd12db6f15"
					+ "47753b5e636bb8b24975780d7d2e3fc2"
					+ "d32a4c480c74de2dc6e3157a632a0287");
	static final TransportId ID = new TransportId(TRANSPORT_ID);

	private static final int SOCKS_PORT = 59050, CONTROL_PORT = 59051;
	private static final int COOKIE_TIMEOUT = 3000; // Milliseconds
	private static final int HOSTNAME_TIMEOUT = 30 * 1000; // Milliseconds
	private static final Pattern ONION =
			Pattern.compile("[a-z2-7]{16}\\.onion");
	private static final Logger LOG =
			Logger.getLogger(TorPlugin.class.getName());

	private final Executor pluginExecutor;
	private final Context appContext;
	private final ShutdownManager shutdownManager;
	private final DuplexPluginCallback callback;
	private final int maxFrameLength;
	private final long maxLatency, pollingInterval;
	private final File torDirectory, torFile, geoIpFile, configFile, doneFile;
	private final File cookieFile, pidFile, hostnameFile;

	private volatile boolean running = false;
	private volatile Process tor = null;
	private volatile int pid = -1;
	private volatile ServerSocket socket = null;
	private volatile Socket controlSocket = null;
	private volatile TorControlConnection controlConnection = null;

	TorPlugin(Executor pluginExecutor, Context appContext,
			ShutdownManager shutdownManager, DuplexPluginCallback callback,
			int maxFrameLength, long maxLatency, long pollingInterval) {
		this.pluginExecutor = pluginExecutor;
		this.appContext = appContext;
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

	public String getName() {
		return "TOR_PLUGIN_NAME";
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
			if(LOG.isLoggable(INFO)) LOG.info("Tor is already running");
			if(readPidFile() == -1) {
				controlSocket.close();
				killZombieProcess();
				startProcess = true;
			}
		} catch(IOException e) {
			if(LOG.isLoggable(INFO)) LOG.info("Tor is not running");
			startProcess = true;
		}
		if(startProcess) {
			// Install the binary, GeoIP database and config file if necessary
			if(!isInstalled() && !install()) {
				if(LOG.isLoggable(INFO)) LOG.info("Could not install Tor");
				return false;
			}
			if(LOG.isLoggable(INFO)) LOG.info("Starting Tor");
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
					if(LOG.isLoggable(WARNING))
						LOG.warning("Auth cookie not created");
					listFiles(torDirectory);
					return false;
				}
			} catch(InterruptedException e1) {
				if(LOG.isLoggable(WARNING))
					LOG.warning("Interrupted while starting Tor");
				return false;
			}
			// Now we should be able to connect to the new process
			controlSocket = new Socket("127.0.0.1", CONTROL_PORT);
		}
		// Read the PID of the Tor process so we can kill it if necessary
		pid = readPidFile();
		// Create a shutdown hook to ensure the Tor process is killed
		shutdownManager.addShutdownHook(new Runnable() {
			public void run() {
				if(tor != null) {
					if(LOG.isLoggable(INFO))
						LOG.info("Killing Tor via destroy()");
					tor.destroy();
				}
				if(pid != -1) {
					if(LOG.isLoggable(INFO))
						LOG.info("Killing Tor via killProcess(" + pid + ")");
					android.os.Process.killProcess(pid);
				}
			}
		});
		// Open a control connection and authenticate using the cookie file
		controlConnection = new TorControlConnection(controlSocket);
		controlConnection.authenticate(read(cookieFile));
		// Register to receive events from the Tor process
		controlConnection.setEventHandler(this);
		controlConnection.setEvents(Arrays.asList("NOTICE", "WARN", "ERR"));
		running = true;
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
				if(LOG.isLoggable(WARNING))
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
				if(LOG.isLoggable(WARNING))
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
			if(LOG.isLoggable(WARNING)) LOG.warning("Could not read PID file");
		} catch(NumberFormatException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning("Could not parse PID file");
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
			while(scanner.hasNextLine()) {
				String[] columns = scanner.nextLine().split("\\s+");
				if(columns.length < 3) break;
				int pid = Integer.parseInt(columns[1]);
				String name = columns[columns.length - 1];
				if(name.contains(packageName) && name.endsWith("/tor")) {
					if(LOG.isLoggable(INFO))
						LOG.info("Killing zombie process " + pid);
					android.os.Process.killProcess(pid);
				}
			}
			scanner.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING))
				LOG.warning("Could not parse ps output");
		} catch(SecurityException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning("Could not execute ps");
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
			if(LOG.isLoggable(INFO)) LOG.info("Creating hidden service");
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
					if(LOG.isLoggable(WARNING))
						LOG.warning("Hidden service not created");
					listFiles(torDirectory);
					return;
				}
				if(!running) return;
			} catch(IOException e) {
				if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			} catch(InterruptedException e) {
				if(LOG.isLoggable(WARNING))
					LOG.warning("Interrupted while creating hidden service");
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
		while(true) {
			Socket s;
			try {
				s = ss.accept();
			} catch(IOException e) {
				// This is expected when the socket is closed
				if(LOG.isLoggable(INFO)) LOG.log(INFO, e.toString(), e);
				tryToClose(ss);
				return;
			}
			if(LOG.isLoggable(INFO)) LOG.info("Connection received");
			TorTransportConnection conn = new TorTransportConnection(this, s);
			callback.incomingConnectionCreated(conn);
			if(!running) return;
		}
	}

	public void stop() throws IOException {
		running = false;
		if(socket != null) tryToClose(socket);
		try {
			if(LOG.isLoggable(INFO)) LOG.info("Stopping Tor");
			if(controlSocket == null)
				controlSocket = new Socket("127.0.0.1", CONTROL_PORT);
			if(controlConnection == null) {
				controlConnection = new TorControlConnection(controlSocket);
				controlConnection.authenticate(read(cookieFile));
			}
			controlConnection.shutdownTor("TERM");
			controlSocket.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			if(LOG.isLoggable(INFO)) LOG.info("Killing Tor");
			if(tor != null) tor.destroy();
			if(pid != -1) android.os.Process.killProcess(pid);
		}
	}

	public boolean shouldPoll() {
		return true;
	}

	public long getPollingInterval() {
		return pollingInterval;
	}

	public void poll(Collection<ContactId> connected) {
		if(!running) return;
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
		if(!running) return null;
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
			if(LOG.isLoggable(INFO)) LOG.log(INFO, e.toString(), e);
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

	public void circuitStatus(String status, String circID, String path) {}

	public void streamStatus(String status, String streamID, String target) {}

	public void orConnStatus(String status, String orName) {}

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
}
