package org.briarproject.android;

import android.app.Application;
import android.content.Context;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.briarproject.CoreModule;
import org.briarproject.R;
import org.briarproject.android.report.BriarReportPrimer;
import org.briarproject.android.report.BriarReportSenderFactory;
import org.briarproject.android.report.DevReportActivity;

import java.util.logging.Logger;

@ReportsCrashes(
		reportPrimerClass = BriarReportPrimer.class,
		logcatArguments = {"-d", "-v", "time", "*:I"},
		reportSenderFactoryClasses = {BriarReportSenderFactory.class},
		mode = ReportingInteractionMode.DIALOG,
		reportDialogClass = DevReportActivity.class,
		resDialogOkToast = R.string.dev_report_saved,
		deleteOldUnsentReportsOnApplicationStart = false
)
public class BriarApplicationImpl extends Application
		implements BriarApplication {

	private static final Logger LOG =
			Logger.getLogger(BriarApplicationImpl.class.getName());

	private AndroidComponent applicationComponent;

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);
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

	@Override
	public AndroidComponent getApplicationComponent() {
		return applicationComponent;
	}
}
