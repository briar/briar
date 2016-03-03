package org.briarproject.android;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.logging.Logger;

import android.app.Application;
import android.content.Context;

public class BriarApplication extends Application {

	private static final Logger LOG =
			Logger.getLogger(BriarApplication.class.getName());

	private AndroidComponent applicationComponent;

	@Override
	public void onCreate() {
		super.onCreate();
		LOG.info("Created");
		UncaughtExceptionHandler oldHandler =
				Thread.getDefaultUncaughtExceptionHandler();
		Context ctx = getApplicationContext();
		CrashHandler newHandler = new CrashHandler(ctx, oldHandler);
		Thread.setDefaultUncaughtExceptionHandler(newHandler);

		applicationComponent = DaggerAndroidComponent.builder()
				.appModule(new AppModule(this))
				.androidModule(new AndroidModule())
				.build();
	}

	public AndroidComponent getApplicationComponent() {
		return applicationComponent;
	}
}
