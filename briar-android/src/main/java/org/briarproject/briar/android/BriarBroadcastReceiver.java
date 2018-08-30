package org.briarproject.briar.android;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.PowerManager;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.POWER_SERVICE;
import static android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED;
import static android.content.Intent.ACTION_BATTERY_CHANGED;
import static android.content.Intent.ACTION_POWER_CONNECTED;
import static android.content.Intent.ACTION_POWER_DISCONNECTED;
import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_SCREEN_ON;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.os.BatteryManager.EXTRA_LEVEL;
import static android.os.BatteryManager.EXTRA_PLUGGED;
import static android.os.BatteryManager.EXTRA_SCALE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED;
import static android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED;

public class BriarBroadcastReceiver extends WakefulBroadcastReceiver {

	IntentFilter getIntentFilter() {
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_SCREEN_ON);
		filter.addAction(ACTION_SCREEN_OFF);
		filter.addAction(ACTION_BATTERY_CHANGED);
		filter.addAction(ACTION_POWER_CONNECTED);
		filter.addAction(ACTION_POWER_DISCONNECTED);
		if (SDK_INT >= 21) filter.addAction(ACTION_POWER_SAVE_MODE_CHANGED);
		if (SDK_INT >= 23) filter.addAction(ACTION_DEVICE_IDLE_MODE_CHANGED);
		filter.addAction(ACTION_AIRPLANE_MODE_CHANGED);
		filter.addAction(CONNECTIVITY_ACTION);
		return filter;
	}

	@Override
	public void onReceive(Context ctx, Intent i) {
		String action = i.getAction();
		if (ACTION_SCREEN_ON.equals(action)) {
			Log.i("DEVICE_STATUS", "Screen on");
		} else if (ACTION_SCREEN_OFF.equals(action)) {
			Log.i("DEVICE_STATUS", "Screen off");
		} else if (ACTION_BATTERY_CHANGED.equals(action)) {
			int level = i.getIntExtra(EXTRA_LEVEL, -1);
			int scale = i.getIntExtra(EXTRA_SCALE, -1);
			int plugged = i.getIntExtra(EXTRA_PLUGGED, -1);
			Log.i("DEVICE_STATUS", "Battery level: " + (level / (float) scale)
					+ ", plugged: " + (plugged != 0));
		} else if (ACTION_POWER_CONNECTED.equals(action)) {
			Log.i("DEVICE_STATUS", "Power connected");
		} else if (ACTION_POWER_DISCONNECTED.equals(action)) {
			Log.i("DEVICE_STATUS", "Power disconnected");
		} else if (SDK_INT >= 21
				&& ACTION_POWER_SAVE_MODE_CHANGED.equals(action)) {
			PowerManager pm = (PowerManager)
					ctx.getSystemService(POWER_SERVICE);
			Log.i("DEVICE_STATUS", "Power save mode: " + pm.isPowerSaveMode());
		} else if (SDK_INT >= 23
				&& ACTION_DEVICE_IDLE_MODE_CHANGED.equals(action)) {
			PowerManager pm = (PowerManager)
					ctx.getSystemService(POWER_SERVICE);
			Log.i("DEVICE_STATUS", " Idle mode: " + pm.isDeviceIdleMode());
		} else if (ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
			Log.i("DEVICE_STATUS",
					"Airplane mode: " + i.getBooleanExtra("state", false));
		} else if (CONNECTIVITY_ACTION.equals(action)) {
			ConnectivityManager cm = (ConnectivityManager)
					ctx.getSystemService(CONNECTIVITY_SERVICE);
			NetworkInfo net = cm.getActiveNetworkInfo();
			boolean online = net != null && net.isConnected();
			boolean wifi = net != null && net.getType() == TYPE_WIFI;
			Log.i("DEVICE_STATUS", "Online: " + online + ", wifi: " + wifi);
		}
	}
}
