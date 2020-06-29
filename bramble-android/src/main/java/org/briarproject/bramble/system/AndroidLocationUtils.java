package org.briarproject.bramble.system;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.LocationUtils;

import java.util.Locale;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.content.Context.TELEPHONY_SERVICE;

@NotNullByDefault
class AndroidLocationUtils implements LocationUtils {

	private static final Logger LOG =
			Logger.getLogger(AndroidLocationUtils.class.getName());

	private final Context appContext;

	@Inject
	AndroidLocationUtils(Application app) {
		appContext = app.getApplicationContext();
	}

	/**
	 * This guesses the current country from the first of these sources that
	 * succeeds (also in order of likelihood of being correct):
	 *
	 * <ul>
	 * <li>Phone network. This works even when no SIM card is inserted, or a
	 *     foreign SIM card is inserted.</li>
	 * <li>SIM card. This is only an heuristic and assumes the user is not
	 *     roaming.</li>
	 * <li>User locale. This is an even worse heuristic.</li>
	 * </ul>
	 *
	 * Note: this is very similar to <a href="https://android.googlesource.com/platform/frameworks/base/+/cd92588%5E/location/java/android/location/CountryDetector.java">
	 * this API</a> except it seems that Google doesn't want us to use it for
	 * some reason - both that class and {@code Context.COUNTRY_CODE} are
	 * annotated {@code @hide}.
	 */
	@Override
	@SuppressLint("DefaultLocale")
	public String getCurrentCountry() {
		String countryCode = getCountryFromPhoneNetwork();
		if (!TextUtils.isEmpty(countryCode)) return countryCode.toUpperCase();
		LOG.info("Falling back to SIM card country");
		countryCode = getCountryFromSimCard();
		if (!TextUtils.isEmpty(countryCode)) return countryCode.toUpperCase();
		LOG.info("Falling back to user-defined locale");
		return Locale.getDefault().getCountry();
	}

	private String getCountryFromPhoneNetwork() {
		Object o = appContext.getSystemService(TELEPHONY_SERVICE);
		TelephonyManager tm = (TelephonyManager) o;
		return tm == null ? "" : tm.getNetworkCountryIso();
	}

	private String getCountryFromSimCard() {
		Object o = appContext.getSystemService(TELEPHONY_SERVICE);
		TelephonyManager tm = (TelephonyManager) o;
		return tm == null ? "" : tm.getSimCountryIso();
	}
}
