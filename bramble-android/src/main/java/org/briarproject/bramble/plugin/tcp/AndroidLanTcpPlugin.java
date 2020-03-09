package org.briarproject.bramble.plugin.tcp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import org.briarproject.bramble.PoliteExecutor;
import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.network.event.NetworkStatusEvent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.settings.Settings;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.net.SocketFactory;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.WIFI_SERVICE;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.os.Build.VERSION.SDK_INT;
import static java.util.Collections.emptyList;
import static java.util.Collections.list;
import static java.util.Collections.singletonList;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.INACTIVE;
import static org.briarproject.bramble.util.IoUtils.tryToClose;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
class AndroidLanTcpPlugin extends LanTcpPlugin {

	private static final Logger LOG =
			getLogger(AndroidLanTcpPlugin.class.getName());

	private final Executor connectionStatusExecutor;
	private final ConnectivityManager connectivityManager;
	@Nullable
	private final WifiManager wifiManager;

	private volatile SocketFactory socketFactory;

	AndroidLanTcpPlugin(Executor ioExecutor, Context appContext,
			Backoff backoff, PluginCallback callback, int maxLatency,
			int maxIdleTime, int connectionTimeout) {
		super(ioExecutor, backoff, callback, maxLatency, maxIdleTime,
				connectionTimeout);
		// Don't execute more than one connection status check at a time
		connectionStatusExecutor =
				new PoliteExecutor("AndroidLanTcpPlugin", ioExecutor, 1);
		ConnectivityManager connectivityManager = (ConnectivityManager)
				appContext.getSystemService(CONNECTIVITY_SERVICE);
		if (connectivityManager == null) throw new AssertionError();
		this.connectivityManager = connectivityManager;
		wifiManager = (WifiManager) appContext.getApplicationContext()
				.getSystemService(WIFI_SERVICE);
		socketFactory = SocketFactory.getDefault();
	}

	@Override
	public void start() {
		if (used.getAndSet(true)) throw new IllegalStateException();
		initialisePortProperty();
		Settings settings = callback.getSettings();
		state.setStarted(settings.getBoolean(PREF_PLUGIN_ENABLE, false));
		updateConnectionStatus();
	}

	@Override
	protected Socket createSocket() throws IOException {
		return socketFactory.createSocket();
	}

	@Override
	protected List<InetAddress> getUsableLocalInetAddresses(boolean ipv4) {
		Pair<InetAddress, Boolean> wifi = getWifiIpv4Address();
		if (wifi == null) return emptyList();
		if (ipv4) return singletonList(wifi.getFirst());
		InetAddress slaac = getSlaacAddressForInterface(wifi.getFirst());
		return slaac == null ? emptyList() : singletonList(slaac);
	}

	/**
	 * Returns a {@link Pair} where the first element is the IPv4 address of
	 * the wifi interface and the second element is true if this device is
	 * providing an access point, or false if this device is a client. Returns
	 * null if this device isn't connected to wifi as an access point or client.
	 */
	@Nullable
	private Pair<InetAddress, Boolean> getWifiIpv4Address() {
		if (wifiManager == null) return null;
		// If we're connected to a wifi network, return its address
		WifiInfo info = wifiManager.getConnectionInfo();
		if (info != null && info.getIpAddress() != 0) {
			return new Pair<>(intToInetAddress(info.getIpAddress()), false);
		}
		List<InterfaceAddress> ifAddrs = getLocalInterfaceAddresses();
		// If we're providing a normal access point, return its address
		for (InterfaceAddress ifAddr : ifAddrs) {
			if (isAndroidWifiApAddress(ifAddr)) {
				return new Pair<>(ifAddr.getAddress(), true);
			}
		}
		// If we're providing a wifi direct access point, return its address
		for (InterfaceAddress ifAddr : ifAddrs) {
			if (isAndroidWifiDirectApAddress(ifAddr)) {
				return new Pair<>(ifAddr.getAddress(), true);
			}
		}
		// Not connected to wifi
		return null;
	}

	/**
	 * Returns true if the given address belongs to a network provided by an
	 * Android access point (including the access point's own address).
	 * <p>
	 * The access point's address is usually 192.168.43.1, but at least one
	 * device (Honor 8A) may use other addresses in the range 192.168.43.0/24.
	 */
	private boolean isAndroidWifiApAddress(InterfaceAddress ifAddr) {
		if (ifAddr.getNetworkPrefixLength() != 24) return false;
		byte[] ip = ifAddr.getAddress().getAddress();
		return ip.length == 4
				&& ip[0] == (byte) 192
				&& ip[1] == (byte) 168
				&& ip[2] == (byte) 43;
	}

	/**
	 * Returns true if the given address belongs to a network provided by an
	 * Android wifi direct legacy mode access point (including the access
	 * point's own address).
	 */
	private boolean isAndroidWifiDirectApAddress(InterfaceAddress ifAddr) {
		if (ifAddr.getNetworkPrefixLength() != 24) return false;
		byte[] ip = ifAddr.getAddress().getAddress();
		return ip.length == 4
				&& ip[0] == (byte) 192
				&& ip[1] == (byte) 168
				&& ip[2] == (byte) 49;
	}

	@Nullable
	private InetAddress getSlaacAddressForInterface(InetAddress wifi) {
		try {
			NetworkInterface iface = NetworkInterface.getByInetAddress(wifi);
			if (iface == null) return null;
			for (InetAddress addr : list(iface.getInetAddresses())) {
				if (isSlaacAddress(addr)) return addr;
			}
			// No suitable address
			return null;
		} catch (SocketException e) {
			logException(LOG, WARNING, e);
			return null;
		}
	}

	private InetAddress intToInetAddress(int ip) {
		byte[] ipBytes = new byte[4];
		ipBytes[0] = (byte) (ip & 0xFF);
		ipBytes[1] = (byte) ((ip >> 8) & 0xFF);
		ipBytes[2] = (byte) ((ip >> 16) & 0xFF);
		ipBytes[3] = (byte) ((ip >> 24) & 0xFF);
		try {
			return InetAddress.getByAddress(ipBytes);
		} catch (UnknownHostException e) {
			// Should only be thrown if address has illegal length
			throw new AssertionError(e);
		}
	}

	// On API 21 and later, a socket that is not created with the wifi
	// network's socket factory may try to connect via another network
	private SocketFactory getSocketFactory() {
		if (SDK_INT < 21) return SocketFactory.getDefault();
		for (Network net : connectivityManager.getAllNetworks()) {
			NetworkInfo info = connectivityManager.getNetworkInfo(net);
			if (info != null && info.getType() == TYPE_WIFI)
				return net.getSocketFactory();
		}
		LOG.warning("Could not find suitable socket factory");
		return SocketFactory.getDefault();
	}

	@Override
	public void eventOccurred(Event e) {
		super.eventOccurred(e);
		if (e instanceof NetworkStatusEvent) updateConnectionStatus();
	}

	private void updateConnectionStatus() {
		connectionStatusExecutor.execute(() -> {
			State s = getState();
			if (s != ACTIVE && s != INACTIVE) return;
			Pair<InetAddress, Boolean> wifi = getWifiIpv4Address();
			if (wifi == null) {
				LOG.info("Not connected to wifi");
				socketFactory = SocketFactory.getDefault();
				// Server sockets may not have been closed automatically when
				// interface was taken down. If any sockets are open, closing
				// them here will cause the sockets to be cleared and the state
				// to be updated in acceptContactConnections()
				if (s == ACTIVE) {
					LOG.info("Closing server sockets");
					tryToClose(state.getServerSocket(true), LOG, WARNING);
					tryToClose(state.getServerSocket(false), LOG, WARNING);
				}
			} else if (wifi.getSecond()) {
				LOG.info("Providing wifi hotspot");
				// There's no corresponding Network object and thus no way
				// to get a suitable socket factory, so we won't be able to
				// make outgoing connections on API 21+ if another network
				// has internet access
				socketFactory = SocketFactory.getDefault();
				if (s == INACTIVE) bind();
			} else {
				LOG.info("Connected to wifi");
				socketFactory = getSocketFactory();
				if (s == INACTIVE) bind();
			}
		});
	}
}