package org.briarproject.bramble.plugin.tcp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static android.content.Context.WIFI_SERVICE;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@NotNullByDefault
class AndroidLanTcpPlugin extends LanTcpPlugin {

	private static final Logger LOG =
			Logger.getLogger(AndroidLanTcpPlugin.class.getName());

	private final Context appContext;
	@Nullable
	private final WifiManager wifiManager;

	@Nullable
	private volatile BroadcastReceiver networkStateReceiver = null;

	AndroidLanTcpPlugin(Executor ioExecutor, Backoff backoff,
			Context appContext, DuplexPluginCallback callback, int maxLatency,
			int maxIdleTime) {
		super(ioExecutor, backoff, callback, maxLatency, maxIdleTime);
		this.appContext = appContext;
		wifiManager = (WifiManager) appContext.getApplicationContext()
				.getSystemService(WIFI_SERVICE);
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

	private class NetworkStateReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context ctx, Intent i) {
			if (!running || wifiManager == null) return;
			WifiInfo info = wifiManager.getConnectionInfo();
			if (info == null || info.getIpAddress() == 0) {
				LOG.info("Not connected to wifi");
				tryToClose(socket);
			} else {
				LOG.info("Connected to wifi");
				if (socket == null || socket.isClosed()) bind();
			}
		}
	}
}
