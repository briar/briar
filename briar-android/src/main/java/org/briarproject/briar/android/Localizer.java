package org.briarproject.briar.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.preference.PreferenceManager;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Locale;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import static android.os.Build.VERSION.SDK_INT;
import static org.briarproject.briar.android.settings.SettingsFragment.LANGUAGE;

@NotNullByDefault
public class Localizer {

	private static Localizer INSTANCE;
	@Nullable
	private final Locale locale;
	private final SharedPreferences sharedPreferences;

	private Localizer(Context context) {
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		locale = getLocaleFromTag(
				sharedPreferences.getString(LANGUAGE, "default"));
	}

	public static synchronized void initialize(Context context) {
		if (INSTANCE == null)
			INSTANCE = new Localizer(context);
	}

	public static synchronized Localizer getInstance() {
		if (INSTANCE == null)
			throw new IllegalStateException("Localizer not initialized");
		return INSTANCE;
	}

	public SharedPreferences getSharedPreferences() {
		return sharedPreferences;
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

	public Context setLocale(Context context) {
		if (locale == null)
			return context;
		Resources res = context.getResources();
		Configuration conf = res.getConfiguration();
		Locale currentLocale;
		if (SDK_INT >= 24) {
			currentLocale = conf.getLocales().get(0);
		} else
			currentLocale = conf.locale;
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
