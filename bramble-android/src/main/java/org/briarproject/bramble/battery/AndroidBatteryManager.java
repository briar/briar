package org.briarproject.bramble.battery;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;

import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.battery.event.BatteryEvent;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.RequiresApi;

import static android.content.Intent.ACTION_BATTERY_CHANGED;
import static android.content.Intent.ACTION_POWER_CONNECTED;
import static android.content.Intent.ACTION_POWER_DISCONNECTED;
import static android.os.BatteryManager.EXTRA_PLUGGED;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED;
import static android.os.PowerManager.ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED;
import static android.os.PowerManager.ACTION_LOW_POWER_STANDBY_ENABLED_CHANGED;
import static android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED;
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
		filter.addAction(ACTION_POWER_SAVE_MODE_CHANGED);
		if (SDK_INT >= 23) filter.addAction(ACTION_DEVICE_IDLE_MODE_CHANGED);
		if (SDK_INT >= 33) {
			filter.addAction(ACTION_LOW_POWER_STANDBY_ENABLED_CHANGED);
			filter.addAction(ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED);
		}
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
			else if (SDK_INT >= 23 &&
					ACTION_DEVICE_IDLE_MODE_CHANGED.equals(action) &&
					LOG.isLoggable(INFO)) {
				LOG.info("Device idle mode changed to: " +
						getPowerManager(ctx).isDeviceIdleMode());
			} else if (SDK_INT >= 23 &&
					ACTION_POWER_SAVE_MODE_CHANGED.equals(action) &&
					LOG.isLoggable(INFO)) {
				LOG.info("Power save mode changed to: " +
						getPowerManager(ctx).isPowerSaveMode());
			} else if (SDK_INT >= 33 && LOG.isLoggable(INFO) &&
					ACTION_LOW_POWER_STANDBY_ENABLED_CHANGED.equals(action)) {
				PowerManager powerManager =
						ctx.getSystemService(PowerManager.class);
				LOG.info("Low power standby now is: " +
						powerManager.isLowPowerStandbyEnabled());
			} else if (SDK_INT >= 33 && LOG.isLoggable(INFO) &&
					ACTION_DEVICE_LIGHT_IDLE_MODE_CHANGED.equals(action)) {
				PowerManager powerManager = getPowerManager(ctx);
				LOG.info("Light idle mode now is: " +
						powerManager.isDeviceLightIdleMode());
			}
		}
	}

	@RequiresApi(api = 23)
	private PowerManager getPowerManager(Context ctx) {
		return ctx.getSystemService(PowerManager.class);
	}
}
