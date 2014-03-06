package org.briarproject.plugins.tor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TorNetworkMetadata {

	// See https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
	// and https://trac.torproject.org/projects/tor/wiki/doc/OONI/censorshipwiki
	// TODO: get a more complete list
	private static final Set<String> BLOCKED_IN_COUNTRIES =
			new HashSet<String>(Arrays.asList("CN", "IR", "SY", "ZZ"));

	public static boolean isTorProbablyBlocked(String countryCode) {
		return BLOCKED_IN_COUNTRIES.contains(countryCode);
	}
}
