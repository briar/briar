package org.briarproject.system;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import org.briarproject.api.system.LocationUtils;

import roboguice.inject.ContextSingleton;
import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.google.inject.Inject;

@ContextSingleton
class AndroidLocationUtils implements LocationUtils {

	private static final Logger LOG =
			Logger.getLogger(AndroidLocationUtils.class.getName());

	final Context context;
	
	@Inject
	public AndroidLocationUtils(Context context) {
		this.context = context;
	}
	
	/**
	 * This guesses the current country from the first of these sources that
	 * succeeds (also in order of likelihood of being correct):
	 *
	 * <ul>
	 * <li>Phone network. This works even when no SIM card is inserted, or a
	 *   foreign SIM card is inserted.</li>
	 * <li><del>Location service (GPS/WiFi/etc).</del> <em>This is disabled for
	 *   now, until we figure out an offline method of converting a long/lat
	 *   into a country code, that doesn't involve a network call.</em>
	 * <li>SIM card. This is only an heuristic and assumes the user is not
	 *   roaming.</li>
	 * <li>User Locale. This is an even worse heuristic.</li>
	 * </ul>
	 *
	 * Note: this is very similar to <a href="https://android.googlesource.com/platform/frameworks/base/+/cd92588%5E/location/java/android/location/CountryDetector.java">
	 * this API</a> except it seems that Google doesn't want us to use it for
	 * some reason - both that class and {@code Context.COUNTRY_CODE} are
	 * annotated {@code @hide}.
	 */
	@SuppressLint("DefaultLocale")
	@Override
	public String getCurrentCountry() {
		String countryCode;
		countryCode = getCountryFromPhoneNetwork();
		if (!TextUtils.isEmpty(countryCode)) {
			return countryCode.toUpperCase(); // android api gives lowercase for some reason
		}
		// When we enable this, we will need to add ACCESS_FINE_LOCATION
		//countryCode = getCountryFromLocation();
		//if (!TextUtils.isEmpty(countryCode)) {
		//	return countryCode;
		//}
		countryCode = getCountryFromSimCard();
		if (!TextUtils.isEmpty(countryCode)) {
			LOG.info("Could not determine current country; fall back to SIM card country.");
			return countryCode.toUpperCase(); // android api gives lowercase for some reason
		}
		LOG.info("Could not determine current country; fall back to user-defined locale.");
		return Locale.getDefault().getCountry();
	}
	
	String getCountryFromPhoneNetwork() {
		TelephonyManager tm = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
		return tm.getNetworkCountryIso();
	}
	
	String getCountryFromSimCard() {
		TelephonyManager tm = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
		return tm.getSimCountryIso();
	}

	// TODO: this is not currently used, because it involves a network call
	// it should be possible to determine country just from the long/lat, but
	// this would involve something like tzdata for countries.
	String getCountryFromLocation() {
		Location location = getLastKnownLocation();
		if (location == null) return null;
		Geocoder code = new Geocoder(context);
		try {
			List<Address> addresses = code.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
			if (addresses.isEmpty()) return null;
			return addresses.get(0).getCountryCode();
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Returns the last location from all location providers.
	 * Since we're only checking the country, we don't care about the accuracy.
	 * If we ever need the accuracy, we can do something like:
	 *   https://code.google.com/p/android-protips-location/source/browse/trunk\
	 *   /src/com/radioactiveyak/location_best_practices/utils/GingerbreadLastLocationFinder.java
	 */
	Location getLastKnownLocation() {
		LocationManager locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
		Location bestResult = null;
		long bestTime = Long.MIN_VALUE;
		for (String provider: locationManager.getAllProviders()) {
			Location location = locationManager.getLastKnownLocation(provider);
			if (location == null) continue;
			long time = location.getTime();
			if (time > bestTime) {
				bestResult = location;
				bestTime = time;
			}
		}
		return bestResult;
	}

}
