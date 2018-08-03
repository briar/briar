package org.briarproject.bramble.network;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.network.NetworkStatus;
import org.briarproject.bramble.api.network.event.NetworkStatusEvent;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.system.Scheduler;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_SCREEN_ON;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.INFO;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class AndroidNetworkManager implements NetworkManager, Service {

	private static final Logger LOG =
			Logger.getLogger(AndroidNetworkManager.class.getName());

	// See android.net.wifi.WifiManager
	private static final String WIFI_AP_STATE_CHANGED_ACTION =
			"android.net.wifi.WIFI_AP_STATE_CHANGED";

	private final ScheduledExecutorService scheduler;
	private final EventBus eventBus;
	private final Context appContext;
	private final AtomicReference<Future<?>> connectivityCheck =
			new AtomicReference<>();
	private final AtomicBoolean used = new AtomicBoolean(false);

	private volatile BroadcastReceiver networkStateReceiver = null;

	@Inject
	AndroidNetworkManager(@Scheduler ScheduledExecutorService scheduler,
			EventBus eventBus, Application app) {
		this.scheduler = scheduler;
		this.eventBus = eventBus;
		this.appContext = app.getApplicationContext();
	}

	@Override
	public void startService() {
		if (used.getAndSet(true)) throw new IllegalStateException();
		// Register to receive network status events
		networkStateReceiver = new NetworkStateReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(CONNECTIVITY_ACTION);
		filter.addAction(ACTION_SCREEN_ON);
		filter.addAction(ACTION_SCREEN_OFF);
		filter.addAction(WIFI_AP_STATE_CHANGED_ACTION);
		if (SDK_INT >= 23) filter.addAction(ACTION_DEVICE_IDLE_MODE_CHANGED);
		appContext.registerReceiver(networkStateReceiver, filter);

	}

	@Override
	public void stopService() {
		if (networkStateReceiver != null)
			appContext.unregisterReceiver(networkStateReceiver);
	}

	@Override
	public NetworkStatus getNetworkStatus() {
		ConnectivityManager cm = (ConnectivityManager)
				appContext.getSystemService(CONNECTIVITY_SERVICE);
		if (cm == null) throw new AssertionError();
		NetworkInfo net = cm.getActiveNetworkInfo();
		boolean connected = net != null && net.isConnected();
		boolean wifi = connected && net.getType() == TYPE_WIFI;
		return new NetworkStatus(connected, wifi);
	}

	private void updateConnectionStatus() {
		eventBus.broadcast(new NetworkStatusEvent(getNetworkStatus()));
	}

	private void scheduleConnectionStatusUpdate(int delay, TimeUnit unit) {
		Future<?> newConnectivityCheck =
				scheduler.schedule(this::updateConnectionStatus, delay, unit);
		Future<?> oldConnectivityCheck =
				connectivityCheck.getAndSet(newConnectivityCheck);
		if (oldConnectivityCheck != null) oldConnectivityCheck.cancel(false);
	}

	private class NetworkStateReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context ctx, Intent i) {
			String action = i.getAction();
			if (LOG.isLoggable(INFO)) LOG.info("Received broadcast " + action);
			updateConnectionStatus();
			if (isSleepOrDozeEvent(action)) {
				// Allow time for the network to be enabled or disabled
				scheduleConnectionStatusUpdate(1, MINUTES);
			} else if (isApEvent(action)) {
				// The state change may be broadcast before the AP address is
				// visible, so delay handling the event
				scheduleConnectionStatusUpdate(5, SECONDS);
			}
		}

		private boolean isSleepOrDozeEvent(@Nullable String action) {
			boolean isSleep = ACTION_SCREEN_ON.equals(action) ||
					ACTION_SCREEN_OFF.equals(action);
			boolean isDoze = SDK_INT >= 23 &&
					ACTION_DEVICE_IDLE_MODE_CHANGED.equals(action);
			return isSleep || isDoze;
		}

		private boolean isApEvent(@Nullable String action) {
			return WIFI_AP_STATE_CHANGED_ACTION.equals(action);
		}
	}
}
