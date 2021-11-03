package org.briarproject.briar.android;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.StrictMode.VmPolicy;
import android.preference.PreferenceManager;

import com.vanniktech.emoji.EmojiManager;
import com.vanniktech.emoji.google.GoogleEmojiProvider;

import org.briarproject.bramble.BrambleAndroidEagerSingletons;
import org.briarproject.bramble.BrambleAppComponent;
import org.briarproject.bramble.BrambleCoreEagerSingletons;
import org.briarproject.briar.BriarCoreEagerSingletons;
import org.briarproject.briar.R;
import org.briarproject.briar.android.logging.CachingLogHandler;
import org.briarproject.briar.android.util.UiUtils;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

import androidx.annotation.NonNull;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.briar.android.TestingConstants.IS_DEBUG_BUILD;

public class BriarApplicationImpl extends Application
		implements BriarApplication {

	private static final Logger LOG =
			getLogger(BriarApplicationImpl.class.getName());

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
		Localizer.getInstance().setLocale(this);
		setTheme(base, prefs);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		if (IS_DEBUG_BUILD) enableStrictMode();

		applicationComponent = createApplicationComponent();
		UncaughtExceptionHandler exceptionHandler =
				applicationComponent.exceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(exceptionHandler);

		Logger rootLogger = getLogger("");
		Handler[] handlers = rootLogger.getHandlers();
		// Disable the Android logger for release builds
		for (Handler handler : handlers) rootLogger.removeHandler(handler);
		if (IS_DEBUG_BUILD) {
			// We can't set the level of the Android logger at runtime, so
			// raise records to the logger's default level
			rootLogger.addHandler(new LevelRaisingHandler(FINE, INFO));
			// Restore the default handlers after the level raising handler
			for (Handler handler : handlers) rootLogger.addHandler(handler);
		}
		CachingLogHandler logHandler = applicationComponent.logHandler();
		rootLogger.addHandler(logHandler);
		rootLogger.setLevel(IS_DEBUG_BUILD ? FINE : INFO);

		LOG.info("Created");

		EmojiManager.install(new GoogleEmojiProvider());
	}

	protected AndroidComponent createApplicationComponent() {
		AndroidComponent androidComponent = DaggerAndroidComponent.builder()
				.appModule(new AppModule(this))
				.build();

		// We need to load the eager singletons directly after making the
		// dependency graphs
		BrambleCoreEagerSingletons.Helper
				.injectEagerSingletons(androidComponent);
		BrambleAndroidEagerSingletons.Helper
				.injectEagerSingletons(androidComponent);
		BriarCoreEagerSingletons.Helper.injectEagerSingletons(androidComponent);
		AndroidEagerSingletons.Helper.injectEagerSingletons(androidComponent);
		return androidComponent;
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
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
		int targetSdk = Build.VERSION.SDK_INT;

		ThreadPolicy.Builder threadPolicy = new ThreadPolicy.Builder();

		// pulled in from threadPolicy.detectAll();
		threadPolicy.detectDiskReads();
		threadPolicy.detectDiskWrites();
		threadPolicy.detectNetwork();

		if (targetSdk >= VERSION_CODES.HONEYCOMB) {
			threadPolicy.detectCustomSlowCalls();
		}
		if (targetSdk >= VERSION_CODES.M) {
			threadPolicy.detectResourceMismatches();
		}
		if (targetSdk >= VERSION_CODES.O) {
			threadPolicy.detectUnbufferedIo();
		}
		// -- end detectAll()

		threadPolicy.penaltyLog();
		StrictMode.setThreadPolicy(threadPolicy.build());

		VmPolicy.Builder vmPolicy = new VmPolicy.Builder();

		// pulled in from vmPolicy.detectAll();
		vmPolicy.detectLeakedSqlLiteObjects();
		if (targetSdk >= VERSION_CODES.HONEYCOMB) {
			vmPolicy.detectActivityLeaks();
			vmPolicy.detectLeakedClosableObjects();
		}
		if (targetSdk >= VERSION_CODES.JELLY_BEAN) {
			vmPolicy.detectLeakedRegistrationObjects();
		}
		if (targetSdk >= VERSION_CODES.JELLY_BEAN_MR2) {
			vmPolicy.detectFileUriExposure();
		}
		if (targetSdk >= VERSION_CODES.M) {
			vmPolicy.detectCleartextNetwork();
		}
		if (targetSdk >= VERSION_CODES.O) {
			vmPolicy.detectContentUriWithoutPermission();
			// do not detect untagged sockets
			// vmPolicy.detectUntaggedSockets();
		}
		if (targetSdk >= VERSION_CODES.Q) {
			vmPolicy.detectCredentialProtectedWhileLocked();
		}
		if (targetSdk >= VERSION_CODES.R) {
			// for some reason this won't compile
			// vmPolicy.detectIncorrectContextUse();
		}
		// -- end detectAll()

		// TODO: add detectUnsafeIntentLaunch() after upgrading to API level 31

		vmPolicy.penaltyLog();
		StrictMode.setVmPolicy(vmPolicy.build());
	}

	@Override
	public BrambleAppComponent getBrambleAppComponent() {
		return applicationComponent;
	}

	@Override
	public AndroidComponent getApplicationComponent() {
		return applicationComponent;
	}

	@Override
	public SharedPreferences getDefaultSharedPreferences() {
		return prefs;
	}

	@Override
	public boolean isRunningInBackground() {
		RunningAppProcessInfo info = new RunningAppProcessInfo();
		ActivityManager.getMyMemoryState(info);
		return (info.importance != IMPORTANCE_FOREGROUND);
	}

	@Override
	public boolean isInstrumentationTest() {
		return false;
	}
}
