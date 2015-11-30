package org.briarproject.android;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static java.util.logging.Level.WARNING;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.logging.Logger;

import android.content.Context;
import android.content.Intent;

class CrashHandler implements UncaughtExceptionHandler {

	private static final Logger LOG =
			Logger.getLogger(CrashHandler.class.getName());

	private final Context ctx;
	private final UncaughtExceptionHandler delegate; // May be null

	CrashHandler(Context ctx, UncaughtExceptionHandler delegate) {
		this.ctx = ctx;
		this.delegate = delegate;
	}

	public void uncaughtException(Thread thread, Throwable throwable) {
		LOG.log(WARNING, "Uncaught exception", throwable);
		// Don't handle more than one exception
		Thread.setDefaultUncaughtExceptionHandler(delegate);
		// Get the stack trace
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		throwable.printStackTrace(pw);
		String stackTrace = sw.toString();
		// Launch the crash reporting dialog
		Intent i = new Intent();
		i.setAction("org.briarproject.REPORT_CRASH");
		i.setFlags(FLAG_ACTIVITY_NEW_TASK);
		i.putExtra("briar.STACK_TRACE", stackTrace);
		i.putExtra("briar.PID", android.os.Process.myPid());
		ctx.startActivity(i);
		// Pass the exception to the default handler, if any
		if (delegate != null) delegate.uncaughtException(thread, throwable);
	}
}
