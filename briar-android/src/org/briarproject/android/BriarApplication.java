package org.briarproject.android;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.logging.Logger;

import android.app.Application;
import android.content.Context;

import org.briarproject.CoreComponent;
import org.briarproject.CoreEagerSingletons;
import org.briarproject.DaggerCoreComponent;
import org.briarproject.plugins.PluginsModuleExtension;
import org.briarproject.system.PlatformModuleExtension;
import org.briarproject.system.SystemModuleExtension;

public class BriarApplication extends Application {

	private static final Logger LOG =
			Logger.getLogger(BriarApplication.class.getName());

	private AndroidComponent applicationComponent;

	@Override
	public void onCreate() {
		super.onCreate();
		LOG.info("Application Created");
		UncaughtExceptionHandler oldHandler =
				Thread.getDefaultUncaughtExceptionHandler();
		Context ctx = getApplicationContext();
		CrashHandler newHandler = new CrashHandler(ctx, oldHandler);
		Thread.setDefaultUncaughtExceptionHandler(newHandler);

		CoreComponent coreComponent = DaggerCoreComponent.builder()
				.systemModule(new SystemModuleExtension(this))
				.platformModule(new PlatformModuleExtension(this))
				.pluginsModule(new PluginsModuleExtension(this))
				.build();

		applicationComponent = DaggerAndroidComponent.builder()
				.appModule(new AppModule(this))
				.coreComponent(coreComponent)
				.build();

		// We need to load the eager singletons directly after making the
		// dependency graphs
		CoreEagerSingletons.initEagerSingletons(coreComponent);
		AndroidEagerSingletons.initEagerSingletons(applicationComponent);
	}

	public AndroidComponent getApplicationComponent() {
		return applicationComponent;
	}
}
