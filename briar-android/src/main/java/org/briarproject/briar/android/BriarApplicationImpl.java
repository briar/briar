package org.briarproject.briar.android;

import android.app.Application;
import android.content.Context;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.StrictMode.VmPolicy;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.briar.BriarCoreModule;
import org.briarproject.briar.R;
import org.briarproject.briar.android.reporting.BriarReportPrimer;
import org.briarproject.briar.android.reporting.BriarReportSenderFactory;
import org.briarproject.briar.android.reporting.DevReportActivity;

import java.util.logging.Logger;

import static org.acra.ReportField.ANDROID_VERSION;
import static org.acra.ReportField.APP_VERSION_CODE;
import static org.acra.ReportField.APP_VERSION_NAME;
import static org.acra.ReportField.BRAND;
import static org.acra.ReportField.BUILD_CONFIG;
import static org.acra.ReportField.CRASH_CONFIGURATION;
import static org.acra.ReportField.CUSTOM_DATA;
import static org.acra.ReportField.DEVICE_FEATURES;
import static org.acra.ReportField.DISPLAY;
import static org.acra.ReportField.INITIAL_CONFIGURATION;
import static org.acra.ReportField.LOGCAT;
import static org.acra.ReportField.PACKAGE_NAME;
import static org.acra.ReportField.PHONE_MODEL;
import static org.acra.ReportField.PRODUCT;
import static org.acra.ReportField.REPORT_ID;
import static org.acra.ReportField.STACK_TRACE;
import static org.acra.ReportField.USER_APP_START_DATE;
import static org.acra.ReportField.USER_CRASH_DATE;
import static org.briarproject.briar.android.TestingConstants.DEFAULT_LOG_LEVEL;
import static org.briarproject.briar.android.TestingConstants.IS_DEBUG_BUILD;

@ReportsCrashes(
		reportPrimerClass = BriarReportPrimer.class,
		logcatArguments = {"-d", "-v", "time", "*:I"},
		reportSenderFactoryClasses = {BriarReportSenderFactory.class},
		mode = ReportingInteractionMode.DIALOG,
		reportDialogClass = DevReportActivity.class,
		resDialogOkToast = R.string.dev_report_saved,
		deleteOldUnsentReportsOnApplicationStart = false,
		customReportContent = {
				REPORT_ID,
				APP_VERSION_CODE, APP_VERSION_NAME, PACKAGE_NAME,
				PHONE_MODEL, ANDROID_VERSION, BRAND, PRODUCT,
				BUILD_CONFIG,
				CUSTOM_DATA,
				STACK_TRACE,
				INITIAL_CONFIGURATION, CRASH_CONFIGURATION,
				DISPLAY, DEVICE_FEATURES,
				USER_APP_START_DATE, USER_CRASH_DATE,
				LOGCAT
		}
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

		if (IS_DEBUG_BUILD) enableStrictMode();
		Logger.getLogger("").setLevel(DEFAULT_LOG_LEVEL);
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

	private void enableStrictMode() {
		ThreadPolicy.Builder threadPolicy = new ThreadPolicy.Builder();
		threadPolicy.detectAll();
		threadPolicy.penaltyLog();
		StrictMode.setThreadPolicy(threadPolicy.build());
		VmPolicy.Builder vmPolicy = new VmPolicy.Builder();
		vmPolicy.detectAll();
		vmPolicy.penaltyLog();
		StrictMode.setVmPolicy(vmPolicy.build());
	}

	@Override
	public AndroidComponent getApplicationComponent() {
		return applicationComponent;
	}
}
