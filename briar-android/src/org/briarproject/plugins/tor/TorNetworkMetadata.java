package org.briarproject.plugins.tor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.briarproject.api.system.LocationUtils;

public class TorNetworkMetadata {

	private static final Logger LOG =
			Logger.getLogger(TorNetworkMetadata.class.getName());

	// for country codes see https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
	// below list from https://trac.torproject.org/projects/tor/wiki/doc/OONI/censorshipwiki
	// TODO: get a more complete list
	public static final Set<String> BLOCKED_IN_COUNTRIES = new HashSet<String>(Arrays.asList(
		"CN",
		"IR",
		"SY",
		//"ET", // possibly lifted       - https://metrics.torproject.org/users.html?graph=userstats-relay-country&start=2012-02-08&end=2014-02-06&country=et&events=off#userstats-relay-country
		//"KZ", // unclear due to botnet - https://metrics.torproject.org/users.html?graph=userstats-relay-country&start=2012-02-08&end=2014-02-06&country=kz&events=off#userstats-relay-country
		//"PH", // unclear due to botnet - https://metrics.torproject.org/users.html?graph=userstats-relay-country&start=2012-02-08&end=2014-02-06&country=ph&events=off#userstats-relay-country
		//"AE", // unclear due to botnet - https://metrics.torproject.org/users.html?graph=userstats-relay-country&start=2012-02-08&end=2014-02-06&country=ae&events=off#userstats-relay-country
		//"GB", // for testing
		"ZZ"
	));

	public static boolean isTorProbablyBlocked(LocationUtils locationUtils) {
		String countryCode = locationUtils.getCurrentCountry();
		if (BLOCKED_IN_COUNTRIES.contains(countryCode)) {
			LOG.info("Tor is probably blocked in your country: " + countryCode);
			return true;
		}
		return false;
	}

}
