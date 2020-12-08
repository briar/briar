package org.briarproject.briar.android.reporting;

import android.content.Context;
import android.content.Intent;
import android.os.Process;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.util.UserFeedback;

import java.lang.Thread.UncaughtExceptionHandler;

import androidx.fragment.app.FragmentActivity;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static org.briarproject.briar.android.reporting.CrashReportActivity.EXTRA_APP_START_TIME;
import static org.briarproject.briar.android.reporting.CrashReportActivity.EXTRA_THROWABLE;

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
		startDevReportActivity(CrashReportActivity.class, e);
		Process.killProcess(Process.myPid());
		System.exit(10);
	}

	public void feedback() {
		startDevReportActivity(FeedbackActivity.class, new UserFeedback());
	}

	private void startDevReportActivity(
			Class<? extends FragmentActivity> activity, Throwable e) {
		final Intent dialogIntent = new Intent(ctx, activity);
		dialogIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
		dialogIntent.putExtra(EXTRA_THROWABLE, e);
		dialogIntent.putExtra(EXTRA_APP_START_TIME, appStartTime);
		ctx.startActivity(dialogIntent);
	}

}
