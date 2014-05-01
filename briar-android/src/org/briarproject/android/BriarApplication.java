package org.briarproject.android;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.logging.Logger;

import android.app.Application;
import android.content.Context;

public class BriarApplication extends Application {

	private static final Logger LOG =
			Logger.getLogger(BriarApplication.class.getName());

	@Override
	public void onCreate() {
		super.onCreate();
		LOG.info("Created");
		UncaughtExceptionHandler oldHandler =
				Thread.getDefaultUncaughtExceptionHandler();
		Context ctx = getApplicationContext();
		CrashHandler newHandler = new CrashHandler(ctx, oldHandler);
		Thread.setDefaultUncaughtExceptionHandler(newHandler);
	}
}
