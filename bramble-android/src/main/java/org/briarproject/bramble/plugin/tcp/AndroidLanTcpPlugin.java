package org.briarproject.bramble.plugin.tcp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.net.SocketFactory;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.WIFI_SERVICE;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.net.wifi.WifiManager.EXTRA_WIFI_STATE;
import static android.os.Build.VERSION.SDK_INT;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;

@NotNullByDefault
class AndroidLanTcpPlugin extends LanTcpPlugin {

	// See android.net.wifi.WifiManager
	private static final String WIFI_AP_STATE_CHANGED_ACTION =
			"android.net.wifi.WIFI_AP_STATE_CHANGED";
	private static final int WIFI_AP_STATE_ENABLED = 13;

	private static final byte[] WIFI_AP_ADDRESS_BYTES =
			{(byte) 192, (byte) 168, 43, 1};
	private static final InetAddress WIFI_AP_ADDRESS;

	private static final Logger LOG =
			Logger.getLogger(AndroidLanTcpPlugin.class.getName());

	static {
		try {
			WIFI_AP_ADDRESS = InetAddress.getByAddress(WIFI_AP_ADDRESS_BYTES);
		} catch (UnknownHostException e) {
			// Should only be thrown if the address has an illegal length
			throw new AssertionError(e);
		}
	}

	private final ScheduledExecutorService scheduler;
	private final Context appContext;
	private final ConnectivityManager connectivityManager;
	@Nullable
	private final WifiManager wifiManager;

	@Nullable
	private volatile BroadcastReceiver networkStateReceiver = null;
	private volatile SocketFactory socketFactory;

	AndroidLanTcpPlugin(Executor ioExecutor, ScheduledExecutorService scheduler,
			Backoff backoff, Context appContext, DuplexPluginCallback callback,
			int maxLatency, int maxIdleTime) {
		super(ioExecutor, backoff, callback, maxLatency, maxIdleTime);
		this.scheduler = scheduler;
		this.appContext = appContext;
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
		running = true;
		// Register to receive network status events
		networkStateReceiver = new NetworkStateReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(CONNECTIVITY_ACTION);
		filter.addAction(WIFI_AP_STATE_CHANGED_ACTION);
		appContext.registerReceiver(networkStateReceiver, filter);
	}

	@Override
	public void stop() {
		running = false;
		if (networkStateReceiver != null)
			appContext.unregisterReceiver(networkStateReceiver);
		tryToClose(socket);
	}

	@Override
	protected Socket createSocket() throws IOException {
		return socketFactory.createSocket();
	}

	@Override
	protected Collection<InetAddress> getLocalIpAddresses() {
		// If the device doesn't have wifi, don't open any sockets
		if (wifiManager == null) return emptyList();
		// If we're connected to a wifi network, use that network
		WifiInfo info = wifiManager.getConnectionInfo();
		if (info != null && info.getIpAddress() != 0)
			return singletonList(intToInetAddress(info.getIpAddress()));
		// If we're running an access point, return its address
		if (super.getLocalIpAddresses().contains(WIFI_AP_ADDRESS))
				return singletonList(WIFI_AP_ADDRESS);
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

	private class NetworkStateReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context ctx, Intent i) {
			if (!running) return;
			if (isApEnabledEvent(i)) {
				// The state change may be broadcast before the AP address is
				// visible, so delay handling the event
				scheduler.schedule(this::handleConnectivityChange, 1, SECONDS);
			} else {
				handleConnectivityChange();
			}
		}

		private void handleConnectivityChange() {
			if (!running) return;
			Collection<InetAddress> addrs = getLocalIpAddresses();
			if (addrs.contains(WIFI_AP_ADDRESS)) {
				LOG.info("Providing wifi hotspot");
				// There's no corresponding Network object and thus no way
				// to get a suitable socket factory, so we won't be able to
				// make outgoing connections on API 21+ if another network
				// has internet access
				socketFactory = SocketFactory.getDefault();
				if (socket == null || socket.isClosed()) bind();
			} else if (addrs.isEmpty()) {
				LOG.info("Not connected to wifi");
				socketFactory = SocketFactory.getDefault();
				tryToClose(socket);
			} else {
				LOG.info("Connected to wifi");
				socketFactory = getSocketFactory();
				if (socket == null || socket.isClosed()) bind();
			}
		}

		private boolean isApEnabledEvent(Intent i) {
			return WIFI_AP_STATE_CHANGED_ACTION.equals(i.getAction()) &&
					i.getIntExtra(EXTRA_WIFI_STATE, 0) == WIFI_AP_STATE_ENABLED;
		}
	}
}
