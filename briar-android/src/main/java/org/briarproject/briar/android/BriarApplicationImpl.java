package org.briarproject.briar.android;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.StrictMode.VmPolicy;
import android.preference.PreferenceManager;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.briar.BriarCoreModule;
import org.briarproject.briar.R;
import org.briarproject.briar.android.logging.CachingLogHandler;
import org.briarproject.briar.android.reporting.BriarReportPrimer;
import org.briarproject.briar.android.reporting.BriarReportSenderFactory;
import org.briarproject.briar.android.reporting.DevReportActivity;
import org.briarproject.briar.android.util.UiUtils;

import java.util.Collection;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
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
import static org.acra.ReportField.PACKAGE_NAME;
import static org.acra.ReportField.PHONE_MODEL;
import static org.acra.ReportField.PRODUCT;
import static org.acra.ReportField.REPORT_ID;
import static org.acra.ReportField.STACK_TRACE;
import static org.acra.ReportField.USER_APP_START_DATE;
import static org.acra.ReportField.USER_CRASH_DATE;
import static org.briarproject.briar.android.TestingConstants.IS_BETA_BUILD;
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
				USER_APP_START_DATE, USER_CRASH_DATE
		}
)
public class BriarApplicationImpl extends Application
		implements BriarApplication {

	private static final Logger LOG =
			Logger.getLogger(BriarApplicationImpl.class.getName());

	private final CachingLogHandler logHandler = new CachingLogHandler();

	private AndroidComponent applicationComponent;
	private volatile SharedPreferences prefs;

	@Override
	protected void attachBaseContext(Context base) {
		if (prefs == null)
			prefs = PreferenceManager.getDefaultSharedPreferences(base);
		// Loading the language needs to be done here.
		Localizer.initialize(prefs);
		super.attachBaseContext(
				Localizer.getInstance().setLocale(base));
		setTheme(base, prefs);
		ACRA.init(this);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		if (IS_DEBUG_BUILD) enableStrictMode();

		Logger rootLogger = Logger.getLogger("");
		if (!IS_DEBUG_BUILD && !IS_BETA_BUILD) {
			// Remove default log handlers so system log is not used
			for (Handler handler : rootLogger.getHandlers()) {
				rootLogger.removeHandler(handler);
			}
		}
		rootLogger.addHandler(logHandler);
		rootLogger.setLevel(IS_DEBUG_BUILD || IS_BETA_BUILD ? FINE : INFO);

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
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		Localizer.getInstance().setLocale(this);
	}

	private void setTheme(Context ctx, SharedPreferences prefs) {
		String theme = prefs.getString("pref_key_theme", null);
		if (theme == null) {
			// set default value
			theme = getString(R.string.pref_theme_light_value);
			prefs.edit().putString("pref_key_theme", theme).apply();
		}
		// set theme
		UiUtils.setTheme(ctx, theme);
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
	public Collection<LogRecord> getRecentLogRecords() {
		return logHandler.getRecentLogRecords();
	}

	@Override
	public AndroidComponent getApplicationComponent() {
		return applicationComponent;
	}

	@Override
	public SharedPreferences getDefaultSharedPreferences() {
		return prefs;
	}
}
