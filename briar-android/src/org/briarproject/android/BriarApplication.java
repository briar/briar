package org.briarproject.android;

import android.app.Application;
import android.content.Context;

import org.briarproject.CoreModule;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.logging.Logger;

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
				.build();

		// We need to load the eager singletons directly after making the
		// dependency graphs
		CoreModule.initEagerSingletons(applicationComponent);
		AndroidEagerSingletons.initEagerSingletons(applicationComponent);
	}

	public AndroidComponent getApplicationComponent() {
		return applicationComponent;
	}
}
