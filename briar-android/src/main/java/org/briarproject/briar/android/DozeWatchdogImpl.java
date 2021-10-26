package org.briarproject.briar.android;

import android.content.Context;

import org.briarproject.android.dontkillmelib.AbstractDozeWatchdogImpl;
import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.briar.api.android.DozeWatchdog;

class DozeWatchdogImpl extends AbstractDozeWatchdogImpl
		implements DozeWatchdog, Service {

	DozeWatchdogImpl(Context appContext) {
		super(appContext);
	}

}
