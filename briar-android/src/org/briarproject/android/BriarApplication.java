package org.briarproject.android;

import android.app.Application;
import android.content.Context;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.briarproject.CoreModule;
import org.briarproject.R;
import org.briarproject.android.util.BriarReportPrimer;

import java.util.logging.Logger;

@ReportsCrashes(
		reportPrimerClass = BriarReportPrimer.class,
		logcatArguments = {"-d", "-v", "time", "*:I"},
		reportSenderFactoryClasses = {
				org.briarproject.android.util.BriarReportSenderFactory.class},
		mode = ReportingInteractionMode.DIALOG,
		reportDialogClass = CrashReportActivity.class,
		resDialogOkToast = R.string.crash_report_saved
)
public class BriarApplication extends Application {

	private static final Logger LOG =
			Logger.getLogger(BriarApplication.class.getName());

	private AndroidComponent applicationComponent;

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);

		// The following line triggers the initialization of ACRA
		ACRA.init(this);
	}

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
