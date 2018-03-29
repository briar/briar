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
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.net.SocketFactory;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.WIFI_SERVICE;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.os.Build.VERSION.SDK_INT;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@NotNullByDefault
class AndroidLanTcpPlugin extends LanTcpPlugin {

	private static final Logger LOG =
			Logger.getLogger(AndroidLanTcpPlugin.class.getName());

	private final Context appContext;
	private final ConnectivityManager connectivityManager;
	@Nullable
	private final WifiManager wifiManager;

	@Nullable
	private volatile BroadcastReceiver networkStateReceiver = null;
	private volatile SocketFactory socketFactory;

	AndroidLanTcpPlugin(Executor ioExecutor, Backoff backoff,
			Context appContext, DuplexPluginCallback callback, int maxLatency,
			int maxIdleTime) {
		super(ioExecutor, backoff, callback, maxLatency, maxIdleTime);
		this.appContext = appContext;
		ConnectivityManager connectivityManager = (ConnectivityManager)
				appContext.getSystemService(CONNECTIVITY_SERVICE);
		if (connectivityManager == null) throw new AssertionError();
		this.connectivityManager = connectivityManager;
		wifiManager = (WifiManager) appContext.getApplicationContext()
				.getSystemService(WIFI_SERVICE);
		socketFactory = getSocketFactory();
	}

	@Override
	public void start() {
		if (used.getAndSet(true)) throw new IllegalStateException();
		running = true;
		// Register to receive network status events
		networkStateReceiver = new NetworkStateReceiver();
		IntentFilter filter = new IntentFilter(CONNECTIVITY_ACTION);
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
		if (wifiManager == null) return emptyList();
		WifiInfo info = wifiManager.getConnectionInfo();
		if (info == null || info.getIpAddress() == 0) return emptyList();
		return singletonList(intToInetAddress(info.getIpAddress()));
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
			if (!running || wifiManager == null) return;
			WifiInfo info = wifiManager.getConnectionInfo();
			if (info == null || info.getIpAddress() == 0) {
				LOG.info("Not connected to wifi");
				socketFactory = SocketFactory.getDefault();
				tryToClose(socket);
			} else {
				LOG.info("Connected to wifi");
				socketFactory = getSocketFactory();
				if (socket == null || socket.isClosed()) bind();
			}
		}
	}
}
