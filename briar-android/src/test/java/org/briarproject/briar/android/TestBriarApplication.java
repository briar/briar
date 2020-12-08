package org.briarproject.briar.android;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.vanniktech.emoji.EmojiManager;
import com.vanniktech.emoji.google.GoogleEmojiProvider;

import org.briarproject.bramble.BrambleAndroidEagerSingletons;
import org.briarproject.bramble.BrambleAppComponent;
import org.briarproject.bramble.BrambleCoreEagerSingletons;
import org.briarproject.briar.BriarCoreEagerSingletons;

import java.util.Collection;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.util.Collections.emptyList;

/**
 * This class only exists to avoid static initialisation of ACRA
 */
public class TestBriarApplication extends Application
		implements BriarApplication {

	private static final Logger LOG =
			Logger.getLogger(TestBriarApplication.class.getName());

	private AndroidComponent applicationComponent;
	private volatile SharedPreferences prefs;

	@Override
	public void onCreate() {
		super.onCreate();
		LOG.info("Created");

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		Localizer.initialize(prefs);
		applicationComponent = DaggerAndroidComponent.builder()
				.appModule(new AppModule(this))
				.build();

		// We need to load the eager singletons directly after making the
		// dependency graphs
		BrambleCoreEagerSingletons.Helper
				.injectEagerSingletons(applicationComponent);
		BrambleAndroidEagerSingletons.Helper
				.injectEagerSingletons(applicationComponent);
		BriarCoreEagerSingletons.Helper
				.injectEagerSingletons(applicationComponent);
		AndroidEagerSingletons.Helper
				.injectEagerSingletons(applicationComponent);
		EmojiManager.install(new GoogleEmojiProvider());
	}

	@Override
	public BrambleAppComponent getBrambleAppComponent() {
		return applicationComponent;
	}

	@Override
	public Collection<LogRecord> getRecentLogRecords() {
		return emptyList();
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
	public void triggerFeedback() {
	}

	@Override
	public boolean isRunningInBackground() {
		return false;
	}
}
