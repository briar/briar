package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.api.lifecycle.IoExecutor;

import java.util.List;

// TODO: Create a module for this so it doesn't have to be public

public interface CircumventionProvider {

	/**
	 * Countries where Tor is blocked, i.e. vanilla Tor connection won't work.
	 *
	 * See https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
	 * and https://trac.torproject.org/projects/tor/wiki/doc/OONI/censorshipwiki
	 */
	String[] BLOCKED = {"CN", "IR", "EG", "BY", "TR", "SY", "VE"};

	/**
	 * Countries where obfs4 or meek bridge connections are likely to work.
	 * Should be a subset of {@link #BLOCKED}.
	 */
	String[] BRIDGES = { "CN", "IR", "EG", "BY", "TR", "SY", "VE" };

	/**
	 * Countries where obfs4 bridges won't work and meek is needed.
	 * Should be a subset of {@link #BRIDGES}.
	 */
	String[] NEEDS_MEEK = {"CN", "IR"};

	boolean isTorProbablyBlocked(String countryCode);

	boolean doBridgesWork(String countryCode);

	boolean needsMeek(String countryCode);

	@IoExecutor
	List<String> getBridges(boolean meek);

}
