package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.api.lifecycle.IoExecutor;

import java.util.List;

public interface CircumventionProvider {

	/**
	 * Countries where Tor is blocked, i.e. vanilla Tor connection won't work.
	 *
	 * See https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
	 * and https://trac.torproject.org/projects/tor/wiki/doc/OONI/censorshipwiki
	 */
	String[] BLOCKED = {"CN", "IR", "EG", "BY", "TR", "SY", "VE"};

	/**
	 * Countries where vanilla bridge connection are likely to work.
	 * Should be a subset of {@link #BLOCKED}.
	 */
	String[] BRIDGES = { "EG", "BY", "TR", "SY", "VE" };

	boolean isTorProbablyBlocked(String countryCode);

	boolean doBridgesWork(String countryCode);

	@IoExecutor
	List<String> getBridges();

}
