package org.briarproject.bramble.api.system;

import android.content.Intent;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface AlarmListener {

	void onAlarm(Intent intent);
}
