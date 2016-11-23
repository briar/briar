package org.briarproject.briar.android;

import android.app.Application;

import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.briar.BriarCoreModule;

import java.util.logging.Logger;

/**
 * This class only exists to avoid static initialisation of ACRA
 */
public class TestBriarApplication extends Application
		implements BriarApplication {

	private static final Logger LOG =
			Logger.getLogger(TestBriarApplication.class.getName());

	private AndroidComponent applicationComponent;

	@Override
	public void onCreate() {
		super.onCreate();
		LOG.info("Created");

		applicationComponent = DaggerAndroidComponent.builder()
				.appModule(new AppModule(this))
				.build();

		// We need to load the eager singletons directly after making the
		// dependency graphs
		BrambleCoreModule.initEagerSingletons(applicationComponent);
		BriarCoreModule.initEagerSingletons(applicationComponent);
		AndroidEagerSingletons.initEagerSingletons(applicationComponent);
	}

	@Override
	public AndroidComponent getApplicationComponent() {
		return applicationComponent;
	}
}
