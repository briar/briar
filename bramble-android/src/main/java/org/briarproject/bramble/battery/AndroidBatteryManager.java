package org.briarproject.bramble.battery;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.battery.event.BatteryEvent;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.content.Intent.ACTION_BATTERY_CHANGED;
import static android.content.Intent.ACTION_POWER_CONNECTED;
import static android.content.Intent.ACTION_POWER_DISCONNECTED;
import static android.os.BatteryManager.EXTRA_PLUGGED;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;

class AndroidBatteryManager implements BatteryManager, Service {

	private static final Logger LOG =
			getLogger(AndroidBatteryManager.class.getName());

	private final Context appContext;
	private final EventBus eventBus;
	private final AtomicBoolean used = new AtomicBoolean(false);

	private volatile BroadcastReceiver batteryReceiver = null;

	@Inject
	AndroidBatteryManager(Application app, EventBus eventBus) {
		this.appContext = app.getApplicationContext();
		this.eventBus = eventBus;
	}

	@Override
	public boolean isCharging() {
		// Get the sticky intent for ACTION_BATTERY_CHANGED
		IntentFilter filter = new IntentFilter(ACTION_BATTERY_CHANGED);
		Intent i = appContext.registerReceiver(null, filter);
		if (i == null) return false;
		int status = i.getIntExtra(EXTRA_PLUGGED, 0);
		return status != 0;
	}

	@Override
	public void startService() {
		if (used.getAndSet(true)) throw new IllegalStateException();
		batteryReceiver = new BatteryReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_POWER_CONNECTED);
		filter.addAction(ACTION_POWER_DISCONNECTED);
		appContext.registerReceiver(batteryReceiver, filter);
	}

	@Override
	public void stopService() {
		if (batteryReceiver != null)
			appContext.unregisterReceiver(batteryReceiver);
	}

	private class BatteryReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context ctx, Intent i) {
			String action = i.getAction();
			if (LOG.isLoggable(INFO)) LOG.info("Received broadcast " + action);
			if (ACTION_POWER_CONNECTED.equals(action))
				eventBus.broadcast(new BatteryEvent(true));
			else if (ACTION_POWER_DISCONNECTED.equals(action))
				eventBus.broadcast(new BatteryEvent(false));
		}
	}
}
