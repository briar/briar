package org.briarproject.bramble.plugin.tcp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.net.ConnectivityManager.TYPE_WIFI;

@NotNullByDefault
class AndroidLanTcpPlugin extends LanTcpPlugin {

	private static final Logger LOG =
			Logger.getLogger(AndroidLanTcpPlugin.class.getName());

	private final Context appContext;

	@Nullable
	private volatile BroadcastReceiver networkStateReceiver = null;

	AndroidLanTcpPlugin(Executor ioExecutor, Backoff backoff,
			Context appContext, DuplexPluginCallback callback, int maxLatency,
			int maxIdleTime) {
		super(ioExecutor, backoff, callback, maxLatency, maxIdleTime);
		this.appContext = appContext;
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

	private class NetworkStateReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context ctx, Intent i) {
			if (!running) return;
			Object o = ctx.getSystemService(CONNECTIVITY_SERVICE);
			ConnectivityManager cm = (ConnectivityManager) o;
			NetworkInfo net = cm.getActiveNetworkInfo();
			if (net != null && net.getType() == TYPE_WIFI && net.isConnected()) {
				LOG.info("Connected to Wi-Fi");
				if (socket == null || socket.isClosed()) bind();
			} else {
				LOG.info("Not connected to Wi-Fi");
				tryToClose(socket);
			}
		}
	}
}
