package org.briarproject.android.dontkillmelib;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static android.content.Context.POWER_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED;

public abstract class AbstractDozeWatchdogImpl {

	private final Context appContext;
	private final AtomicBoolean dozed = new AtomicBoolean(false);
	private final BroadcastReceiver receiver = new DozeBroadcastReceiver();

	public AbstractDozeWatchdogImpl(Context appContext) {
		this.appContext = appContext;
	}

	public boolean getAndResetDozeFlag() {
		return dozed.getAndSet(false);
	}

	public void startService() {
		if (SDK_INT < 23) return;
		IntentFilter filter = new IntentFilter(ACTION_DEVICE_IDLE_MODE_CHANGED);
		appContext.registerReceiver(receiver, filter);
	}

	public void stopService() {
		if (SDK_INT < 23) return;
		appContext.unregisterReceiver(receiver);
	}

	private class DozeBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (SDK_INT < 23) return;
			PowerManager pm =
					(PowerManager) appContext.getSystemService(POWER_SERVICE);
			if (pm.isDeviceIdleMode()) dozed.set(true);
		}
	}
}
