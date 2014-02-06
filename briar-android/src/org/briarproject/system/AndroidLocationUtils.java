package org.briarproject.system;

import java.util.Locale;
import java.util.logging.Logger;

import org.briarproject.api.system.LocationUtils;

import roboguice.inject.ContextSingleton;
import android.annotation.SuppressLint;
import android.content.Context;
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
	
	@SuppressLint("DefaultLocale")
	@Override
	public String getCurrentCountry() {
		String countryCode;
		countryCode = getCountryFromPhoneNetwork();
		if (!TextUtils.isEmpty(countryCode)) {
			return countryCode.toUpperCase(); // android api gives lowercase for some reason
		}
		LOG.warning("Could not determine current country; fall back to user-defined locale");
		return Locale.getDefault().getCountry();
	}
	
	String getCountryFromPhoneNetwork() {
		TelephonyManager tm = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
		return tm.getNetworkCountryIso();
	}
	
}
