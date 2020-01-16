package org.briarproject.bramble.plugin.tcp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import org.briarproject.bramble.PoliteExecutor;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.network.event.NetworkStatusEvent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginCallback;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
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
import static java.util.Collections.singletonList;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.INACTIVE;

@NotNullByDefault
class AndroidLanTcpPlugin extends LanTcpPlugin implements EventListener {

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
		state.setStarted();
		callback.pluginStateChanged(getState());
		updateConnectionStatus();
	}

	@Override
	protected Socket createSocket() throws IOException {
		return socketFactory.createSocket();
	}

	@Override
	protected List<InetAddress> getUsableLocalInetAddresses() {
		// If the device doesn't have wifi, don't open any sockets
		if (wifiManager == null) return emptyList();
		// If we're connected to a wifi network, return its address
		WifiInfo info = wifiManager.getConnectionInfo();
		if (info != null && info.getIpAddress() != 0) {
			return singletonList(intToInetAddress(info.getIpAddress()));
		}
		// If we're running an access point, return its address
		for (InetAddress addr : getLocalInetAddresses()) {
			if (addr.equals(WIFI_AP_ADDRESS)) return singletonList(addr);
			if (addr.equals(WIFI_DIRECT_AP_ADDRESS)) return singletonList(addr);
		}
		// No suitable addresses
		return emptyList();
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
		if (e instanceof NetworkStatusEvent) updateConnectionStatus();
	}

	private void updateConnectionStatus() {
		connectionStatusExecutor.execute(() -> {
			State state = getState();
			if (state != ACTIVE && state != INACTIVE) return;
			List<InetAddress> addrs = getLocalInetAddresses();
			if (addrs.contains(WIFI_AP_ADDRESS)
					|| addrs.contains(WIFI_DIRECT_AP_ADDRESS)) {
				LOG.info("Providing wifi hotspot");
				// There's no corresponding Network object and thus no way
				// to get a suitable socket factory, so we won't be able to
				// make outgoing connections on API 21+ if another network
				// has internet access
				socketFactory = SocketFactory.getDefault();
				if (state == INACTIVE) bind();
			} else if (addrs.isEmpty()) {
				LOG.info("Not connected to wifi");
				socketFactory = SocketFactory.getDefault();
				// Server socket was closed when wifi interface went down
			} else {
				LOG.info("Connected to wifi");
				socketFactory = getSocketFactory();
				if (state == INACTIVE) bind();
			}
		});
	}
}