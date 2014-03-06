package org.briarproject.system;

import static android.content.Context.LOCATION_SERVICE;
import static android.content.Context.TELEPHONY_SERVICE;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import org.briarproject.api.system.LocationUtils;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.google.inject.Inject;

class AndroidLocationUtils implements LocationUtils {

	private static final Logger LOG =
			Logger.getLogger(AndroidLocationUtils.class.getName());

	private final Context appContext;

	@Inject
	public AndroidLocationUtils(Application app) {
		appContext = app.getApplicationContext();
	}

	/**
	 * This guesses the current country from the first of these sources that
	 * succeeds (also in order of likelihood of being correct):
	 *
	 * <ul>
	 * <li>Phone network. This works even when no SIM card is inserted, or a
	 *     foreign SIM card is inserted.</li>
	 * <li><del>Location service (GPS/WiFi/etc).</del> <em>This is disabled for
	 *     now, until we figure out an offline method of converting a long/lat
	 *     into a country code, that doesn't involve a network call.</em>
	 * <li>SIM card. This is only an heuristic and assumes the user is not
	 *     roaming.</li>
	 * <li>User locale. This is an even worse heuristic.</li>
	 * </ul>
	 *
	 * Note: this is very similar to <a href="https://android.googlesource.com/platform/frameworks/base/+/cd92588%5E/location/java/android/location/CountryDetector.java">
	 * this API</a> except it seems that Google doesn't want us to useit for
	 * some reason - both that class and {@code Context.COUNTRY_CODE} are
	 * annotated {@code @hide}.
	 */
	@SuppressLint("DefaultLocale")
	public String getCurrentCountry() {
		String countryCode = getCountryFromPhoneNetwork();
		if(!TextUtils.isEmpty(countryCode)) return countryCode.toUpperCase();
		// Disabled because it involves a network call; requires
		// ACCESS_FINE_LOCATION
		// countryCode = getCountryFromLocation();
		// if(!TextUtils.isEmpty(countryCode)) return countryCode;
		LOG.info("Falling back to SIM card country");
		countryCode = getCountryFromSimCard();
		if(!TextUtils.isEmpty(countryCode)) return countryCode.toUpperCase();
		LOG.info("Falling back to user-defined locale");
		return Locale.getDefault().getCountry();
	}

	private String getCountryFromPhoneNetwork() {
		Object o = appContext.getSystemService(TELEPHONY_SERVICE);
		TelephonyManager tm = (TelephonyManager) o;
		return tm.getNetworkCountryIso();
	}

	private String getCountryFromSimCard() {
		Object o = appContext.getSystemService(TELEPHONY_SERVICE);
		TelephonyManager tm = (TelephonyManager) o;
		return tm.getSimCountryIso();
	}

	// TODO: this is not currently used, because it involves a network call
	// it should be possible to determine country just from the long/lat, but
	// this would involve something like tzdata for countries.
	private String getCountryFromLocation() {
		Location location = getLastKnownLocation();
		if(location == null) return null;
		Geocoder code = new Geocoder(appContext);
		try {
			double lat = location.getLatitude();
			double lon = location.getLongitude();
			List<Address> addresses = code.getFromLocation(lat, lon, 1);
			if(addresses.isEmpty()) return null;
			return addresses.get(0).getCountryCode();
		} catch(IOException e) {
			return null;
		}
	}

	/**
	 * Returns the last location from all location providers, or null if there
	 * is no such location. Since we're only checking the country, we don't
	 * care about the accuracy. If we ever need the accuracy, we can do
	 * something like <a href="https://code.google.com/p/android-protips-location/source/browse/trunk/src/com/radioactiveyak/location_best_practices/utils/GingerbreadLastLocationFinder.java">
	 * this</a>.
	 */
	private Location getLastKnownLocation() {
		Object o = appContext.getSystemService(LOCATION_SERVICE);
		LocationManager locationManager = (LocationManager) o;
		Location bestResult = null;
		long bestTime = Long.MIN_VALUE;
		for(String provider : locationManager.getAllProviders()) {
			Location location = locationManager.getLastKnownLocation(provider);
			if(location == null) continue;
			long time = location.getTime();
			if(time > bestTime) {
				bestResult = location;
				bestTime = time;
			}
		}
		return bestResult;
	}
}
