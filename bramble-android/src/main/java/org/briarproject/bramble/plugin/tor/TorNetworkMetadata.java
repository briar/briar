package org.briarproject.bramble.plugin.tor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

class TorNetworkMetadata {

	/**
	 * Countries where Tor is blocked, i.e. vanilla Tor connection won't work.
	 */
	private static final String[] BLOCKED =
			{"CN", "IR", "EG", "BY", "TR", "SY", "VE"};

	/**
	 * Countries where vanilla bridge connection are likely to work.
	 * Should be a subset of {@link #BLOCKED}.
	 */
	private static final String[] BRIDGES = { "EG", "BY", "TR", "SY", "VE" };

	// See https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
	// and https://trac.torproject.org/projects/tor/wiki/doc/OONI/censorshipwiki
	// TODO: get a more complete list
	private static final Set<String> BLOCKED_IN_COUNTRIES =
			new HashSet<>(Arrays.asList(BLOCKED));
	private static final Set<String> BRIDGES_WORK_IN_COUNTRIES =
			new HashSet<>(Arrays.asList(BRIDGES));

	static boolean isTorProbablyBlocked(String countryCode) {
		return BLOCKED_IN_COUNTRIES.contains(countryCode);
	}

	static boolean doBridgesWork(String countryCode) {
		return BRIDGES_WORK_IN_COUNTRIES.contains(countryCode);
	}

}
