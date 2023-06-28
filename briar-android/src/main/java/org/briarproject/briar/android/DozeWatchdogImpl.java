package org.briarproject.briar.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;

import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.briar.api.android.DozeWatchdog;

import java.util.concurrent.atomic.AtomicBoolean;

import static android.content.Context.POWER_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED;
import static android.os.PowerManager.ACTION_LOW_POWER_STANDBY_ENABLED_CHANGED;

class DozeWatchdogImpl implements DozeWatchdog, Service {

	private final Context appContext;
	private final AtomicBoolean dozed = new AtomicBoolean(false);
	private final BroadcastReceiver receiver = new DozeBroadcastReceiver();

	DozeWatchdogImpl(Context appContext) {
		this.appContext = appContext;
	}

	@Override
	public boolean getAndResetDozeFlag() {
		return dozed.getAndSet(false);
	}

	@Override
	public void startService() {
		if (SDK_INT < 23) return;
		IntentFilter filter = new IntentFilter(ACTION_DEVICE_IDLE_MODE_CHANGED);
		if (SDK_INT >= 33) {
			filter.addAction(ACTION_LOW_POWER_STANDBY_ENABLED_CHANGED);
		}
		appContext.registerReceiver(receiver, filter);
	}

	@Override
	public void stopService() {
		if (SDK_INT < 23) return;
		appContext.unregisterReceiver(receiver);
	}

	private class DozeBroadcastReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (SDK_INT < 23) return;
			String action = intent.getAction();
			PowerManager pm =
					(PowerManager) appContext.getSystemService(POWER_SERVICE);
			if (ACTION_DEVICE_IDLE_MODE_CHANGED.equals(action)) {
				if (pm.isDeviceIdleMode()) dozed.set(true);
			} else if (ACTION_LOW_POWER_STANDBY_ENABLED_CHANGED.equals(
					action)) {
				// pm.isLowPowerStandbyEnabled();
				// TODO what do we do with this now? Disable Tor?
				//  broadcast network disabled events?
			}
		}
	}
}
