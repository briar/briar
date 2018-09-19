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
	private final Locale locale;

	private Localizer(SharedPreferences sharedPreferences) {
		this(Locale.getDefault(), getLocaleFromTag(
				sharedPreferences.getString(LANGUAGE, "default")));
	}

	private Localizer(Locale systemLocale, @Nullable Locale userLocale) {
		this.systemLocale = systemLocale;
		if (userLocale == null) locale = systemLocale;
		else locale = userLocale;
	}

	// Instantiate the Localizer.
	public static synchronized void initialize(SharedPreferences prefs) {
		if (INSTANCE == null)
			INSTANCE = new Localizer(prefs);
	}

	// Reinstantiate the Localizer with the system locale
	public static synchronized void reinitialize() {
		if (INSTANCE != null)
			INSTANCE = new Localizer(INSTANCE.systemLocale, null);
	}

	// Get the current instance.
	public static synchronized Localizer getInstance() {
		if (INSTANCE == null)
			throw new IllegalStateException("Localizer not initialized");
		return INSTANCE;
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
		Locale.setDefault(locale);
		updateConfiguration(conf, locale);
		Configuration systemConfiguration =
				Resources.getSystem().getConfiguration();
		updateConfiguration(systemConfiguration, locale);
		// DateUtils uses the system resources, so we need to update them too.
		// Apparently updateConfiguration is not deprecated here but is below.
		Resources.getSystem().updateConfiguration(systemConfiguration,
				Resources.getSystem().getDisplayMetrics());
		if (SDK_INT >= 17)
			return context.createConfigurationContext(conf);
		//noinspection deprecation
		res.updateConfiguration(conf, res.getDisplayMetrics());
		return context;
	}

	private void updateConfiguration(Configuration conf, Locale locale) {
		if (SDK_INT >= 17) {
			conf.setLocale(locale);
		} else
			conf.locale = locale;
	}
}
