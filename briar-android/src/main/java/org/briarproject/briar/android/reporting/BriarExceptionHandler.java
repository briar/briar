package org.briarproject.briar.android.reporting;

import android.app.Application;
import android.os.Process;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.logging.LogEncrypter;

import java.lang.Thread.UncaughtExceptionHandler;

import javax.inject.Inject;

import static org.briarproject.briar.android.util.UiUtils.startDevReportActivity;

@NotNullByDefault
class BriarExceptionHandler implements UncaughtExceptionHandler {

	private final Application app;
	private final LogEncrypter logEncrypter;
	private final long appStartTime;

	@Inject
	BriarExceptionHandler(Application app, LogEncrypter logEncrypter) {
		this.app = app;
		this.logEncrypter = logEncrypter;
		appStartTime = System.currentTimeMillis();
	}

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		// encrypt logs to disk before handing over to new process
		// the intent has limited space, so we can't reliably store them there.
		byte[] logKey = logEncrypter.encryptLogs();

		// activity runs in its own process, so we can kill the old one
		startDevReportActivity(app.getApplicationContext(),
				CrashReportActivity.class, e, appStartTime, logKey);
		Process.killProcess(Process.myPid());
		System.exit(10);
	}

}
