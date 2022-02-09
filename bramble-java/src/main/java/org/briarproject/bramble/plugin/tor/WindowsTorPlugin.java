package org.briarproject.bramble.plugin.tor;

import com.sun.jna.Library;
import com.sun.jna.Native;

import net.freehaven.tor.control.TorControlConnection;
import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;

import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

@NotNullByDefault
class WindowsTorPlugin extends JavaTorPlugin {

    WindowsTorPlugin(Executor ioExecutor,
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
        super(ioExecutor, wakefulIoExecutor, networkManager, locationUtils,
                torSocketFactory, clock, resourceProvider,
                circumventionProvider, batteryManager, backoff,
                torRendezvousCrypto, callback, architecture,
                maxLatency, maxIdleTime, torDirectory, torSocksPort, torControlPort);
    }

    protected File getTorExecutableFile() {
        return new File(torDirectory, "tor.exe");
    }

    protected InputStream getConfigInputStream() {
        StringBuilder strb = new StringBuilder();
        append(strb, "ControlPort", torControlPort);
        append(strb, "CookieAuthentication", 1);
        append(strb, "DisableNetwork", 1);
        append(strb, "RunAsDaemon", 1);
        append(strb, "SafeSocks", 1);
        append(strb, "SocksPort", torSocksPort);
        InputStream inputStream = new ByteArrayInputStream(
                strb.toString().getBytes(Charset.forName("UTF-8")));
        InputStream windowsPaths = new ByteArrayInputStream(getTorrcPaths());
        inputStream = new SequenceInputStream(inputStream, windowsPaths);
        return inputStream;
    }

    private byte[] getTorrcPaths() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("GeoIPFile ");
        sb.append(geoIpFile.getAbsolutePath());
        sb.append("\n");
        sb.append("GeoIPv6File ");
        sb.append(geoIpFile.getAbsolutePath());
        sb.append("6");
        sb.append("\n");
        sb.append("DataDirectory ");
        sb.append(torDirectory);
        sb.append("\\.tor");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void start() throws PluginException {
        /*
        TODO:
        - properly handle and throw PluginExceptions etc.
        - absolute paths in Windows torrc (Linux too?)
        - don't do 10 seconds sleep in main thread
         */
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
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                Process torProcess;
                ProcessBuilder pb =
                        new ProcessBuilder(torPath, "-f", configPath, OWNER, pid);
                pb.redirectErrorStream(true); // logged only first line on Windows otherwise
                Map<String, String> env = pb.environment();
                env.put("HOME", torDirectory.getAbsolutePath());
                pb.directory(torDirectory);
                try {
                    torProcess = pb.start();
                    // Log the process's standard output until it detaches
                    if (LOG.isLoggable(INFO)) {
                        Scanner stdout = new Scanner(torProcess.getInputStream());
                        while (stdout.hasNextLine()) {
                            if (stdout.hasNextLine()) {
                                LOG.info(stdout.nextLine());
                            }
                        }
                        stdout.close();
                    }
                    try {
                        // Wait for the process to detach or exit
                        int exit = torProcess.waitFor();
                        if (exit != 0) {
                            if (LOG.isLoggable(WARNING))
                                LOG.warning("Tor exited with value " + exit);
                        }
                        // Wait for the auth cookie file to be created/updated
                        long start = clock.currentTimeMillis();
                        while (cookieFile.length() < 32) {
                            if (clock.currentTimeMillis() - start > COOKIE_TIMEOUT_MS) {
                                LOG.warning("Auth cookie not created");
                                if (LOG.isLoggable(INFO)) listFiles(torDirectory);
                            }
                            Thread.sleep(COOKIE_POLLING_INTERVAL_MS);
                        }
                        LOG.info("Auth cookie created");
                    } catch (InterruptedException e) {
                        LOG.warning("Interrupted while starting Tor");
                        Thread.currentThread().interrupt();
                        e.printStackTrace();
                    }
                } catch (SecurityException | IOException e) {
                    e.printStackTrace();
                }
            }
        });
        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
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

    @Override
    protected int getProcessId() {
        return CLibrary.INSTANCE._getpid();
    }

    private interface CLibrary extends Library {

        CLibrary INSTANCE = Native.loadLibrary("msvcrt", CLibrary.class);

        int _getpid();
    }
}
