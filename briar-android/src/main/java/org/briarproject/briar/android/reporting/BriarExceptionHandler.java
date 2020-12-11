package org.briarproject.briar.android.reporting;

import android.content.Context;
import android.os.Process;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.lang.Thread.UncaughtExceptionHandler;

import static org.briarproject.briar.android.util.UiUtils.startDevReportActivity;

@NotNullByDefault
public class BriarExceptionHandler implements UncaughtExceptionHandler {

	private final Context ctx;
	private final long appStartTime;

	public BriarExceptionHandler(Context ctx) {
		this.ctx = ctx;
		this.appStartTime = System.currentTimeMillis();
	}

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		// activity runs in its own process, so we can kill the old one
		startDevReportActivity(ctx, CrashReportActivity.class, e, appStartTime);
		Process.killProcess(Process.myPid());
		System.exit(10);
	}

}
