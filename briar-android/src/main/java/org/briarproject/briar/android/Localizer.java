package org.briarproject.briar.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.support.v4.text.TextUtilsCompat;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Locale;

import javax.annotation.Nullable;

import static android.os.Build.VERSION.SDK_INT;
import static android.support.v4.view.ViewCompat.LAYOUT_DIRECTION_LTR;
import static org.briarproject.briar.android.settings.SettingsFragment.LANGUAGE;

@NotNullByDefault
public class Localizer {

	// Locking: class
	@Nullable
	private static Localizer INSTANCE;
	private final Locale systemLocale;
	@Nullable
	private final Locale locale;

	private Localizer(SharedPreferences sharedPreferences) {
		this(Locale.getDefault(), getLocaleFromTag(
				sharedPreferences.getString(LANGUAGE, "default")));
	}

	private Localizer(Locale systemLocale, @Nullable Locale userLocale) {
		this.systemLocale = systemLocale;
		locale = userLocale;
		setLocaleAndSystemConfiguration(locale);
	}

	private Localizer(Locale systemLocale) {
		this.systemLocale = systemLocale;
		locale = null;
		setLocaleAndSystemConfiguration(systemLocale);
	}


	// Instantiate the Localizer.
	public static synchronized void initialize(SharedPreferences prefs) {
		if (INSTANCE == null)
			INSTANCE = new Localizer(prefs);
	}

	// Reinstantiate the Localizer with the system locale
	public static synchronized void reinitialize(Context appContext) {
		if (INSTANCE != null && INSTANCE.locale != null) {
			INSTANCE = new Localizer(INSTANCE.systemLocale);
			INSTANCE.forceLocale(appContext, INSTANCE.systemLocale);
		}
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
		if (locale == null || SDK_INT < 17) return context;
		Resources res = context.getResources();
		Configuration conf = res.getConfiguration();
		updateConfiguration(conf, locale);
		return context.createConfigurationContext(conf);
	}

	// For API < 17 only.
	public void setLocaleLegacy(Context appContext) {
		if (SDK_INT >= 17 || locale == null) return;
		forceLocale(appContext, locale);
	}

	// Forces the update of the resources through the deprecated API.
	private void forceLocale(Context context, Locale locale) {
		Resources res = context.getResources();
		Configuration conf = res.getConfiguration();
		updateConfiguration(conf, locale);
		//noinspection deprecation
		res.updateConfiguration(conf, res.getDisplayMetrics());
	}

	private void updateConfiguration(Configuration conf, Locale locale) {
		if (SDK_INT >= 17) {
			conf.setLocale(locale);
		} else
			conf.locale = locale;
	}

	private void setLocaleAndSystemConfiguration(@Nullable Locale locale) {
		if (locale == null) return;
		Locale.setDefault(locale);
		if (SDK_INT >= 23) return;
		Configuration systemConfiguration =
				Resources.getSystem().getConfiguration();
		updateConfiguration(systemConfiguration, locale);
		// DateUtils uses the system resources, so we need to update them too.
		//noinspection deprecation
		Resources.getSystem().updateConfiguration(systemConfiguration,
				Resources.getSystem().getDisplayMetrics());
	}

	public void applicationConfigurationChanged(Context appContext,
			Configuration newConfig) {
		if (SDK_INT >= 24) {
			if (newConfig.getLocales().get(0) == locale) return;
		} else {
			if (newConfig.locale == locale) return;
		}
		setLocaleAndSystemConfiguration(locale);
		if (SDK_INT < 17) setLocaleLegacy(appContext);
	}

	/**
	 * Indicates whether the language represented by locale
	 * should be offered to the user on this device.
	 * * Android doesn't pick up Asturian on API < 21
	 * * Android can't render Devanagari characters on API 15.
	 * * RTL languages are supported since API >= 17
	 */
	public static boolean isLocaleSupported(Locale locale) {
		if (SDK_INT >= 21) return true;
		if (locale.getLanguage().equals("ast")) return false;
		if (SDK_INT == 15 && locale.getLanguage().equals("hi")) return false;
		if (SDK_INT >= 17) return true;
		return isLeftToRight(locale);
	}

	// Exclude RTL locales on API < 17, they won't be laid out correctly
	private static boolean isLeftToRight(Locale locale) {
		// TextUtilsCompat returns the wrong direction for Hebrew on some phones
		String language = locale.getLanguage();
		if (language.equals("iw") || language.equals("he")) return false;
		int direction = TextUtilsCompat.getLayoutDirectionFromLocale(locale);
		return direction == LAYOUT_DIRECTION_LTR;
	}

}
