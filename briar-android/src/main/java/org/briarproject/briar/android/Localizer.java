package org.briarproject.briar.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Locale;

import javax.annotation.Nullable;

import static android.os.Build.VERSION.SDK_INT;
import static org.briarproject.briar.android.settings.SettingsFragment.LANGUAGE;

@NotNullByDefault
public class Localizer {

	// Locking: class
	@Nullable
	private static Localizer INSTANCE;
	private final Locale systemLocale;
	// Locking: this
	@Nullable
	private Locale locale;

	private Localizer(SharedPreferences sharedPreferences) {
		systemLocale = Locale.getDefault();
		locale = getLocaleFromTag(
				sharedPreferences.getString(LANGUAGE, "default"));
	}

	public static synchronized void initialize(SharedPreferences prefs) {
		if (INSTANCE == null)
			INSTANCE = new Localizer(prefs);
	}

	public static synchronized Localizer getInstance() {
		if (INSTANCE == null)
			throw new IllegalStateException("Localizer not initialized");
		return INSTANCE;
	}

	// Reset to the system locale
	public synchronized void reset() {
		locale = systemLocale;
	}

	// Get Locale from BCP-47 tag
	@Nullable
	public static Locale getLocaleFromTag(String tag) {
		if (tag.equals("default"))
			return null;
		if (SDK_INT >= 21) {
			return Locale.forLanguageTag(tag);
		}
		if (tag.contains("-")) {
			String[] langArray = tag.split("-");
			return new Locale(langArray[0], langArray[1]);
		} else
			return new Locale(tag);
	}

	// Returns the localized version of context
	public Context setLocale(Context context) {
		Resources res = context.getResources();
		Configuration conf = res.getConfiguration();
		Locale currentLocale;
		if (SDK_INT >= 24) {
			currentLocale = conf.getLocales().get(0);
		} else
			currentLocale = conf.locale;
		synchronized (this) {
			if (locale == null) {
				// Detect if the user changed the system language
				if (systemLocale.equals(currentLocale))
					return context;
				locale = systemLocale;
			}
		}
		if (locale.equals(currentLocale))
			return context;
		Locale.setDefault(locale);
		if (SDK_INT >= 17) {
			conf.setLocale(locale);
			context.createConfigurationContext(conf);
		} else
			conf.locale = locale;
		//noinspection deprecation
		res.updateConfiguration(conf, res.getDisplayMetrics());
		return context;
	}
}
