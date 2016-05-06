package briarproject.activity;

import android.app.Application;

import org.briarproject.CoreModule;
import org.briarproject.android.AndroidComponent;
import org.briarproject.android.AndroidEagerSingletons;
import org.briarproject.android.AppModule;
import org.briarproject.android.BriarApplication;
import org.briarproject.android.DaggerAndroidComponent;

import java.util.logging.Logger;

/**
 * This Class only exists to get around ACRA
 */
public class TestBriarApplicationImp extends Application implements
		BriarApplication{

	private static final Logger LOG =
			Logger.getLogger(TestBriarApplicationImp.class.getName());

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
		CoreModule.initEagerSingletons(applicationComponent);
		AndroidEagerSingletons.initEagerSingletons(applicationComponent);
	}

	public AndroidComponent getApplicationComponent() {
		return applicationComponent;
	}
}
