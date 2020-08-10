package org.briarproject.bramble.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.briarproject.bramble.BrambleApplication;

public class AlarmReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context ctx, Intent intent) {
		BrambleApplication app =
				(BrambleApplication) ctx.getApplicationContext();
		app.getBrambleAppComponent().alarmListener().onAlarm(intent);
	}
}
