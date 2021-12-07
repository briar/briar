package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.List;

@NotNullByDefault
public interface CircumventionProvider {

	enum BridgeType {
		DEFAULT_OBFS4,
		NON_DEFAULT_OBFS4,
		MEEK
	}

	/**
	 * Countries where Tor is blocked, i.e. vanilla Tor connection won't work.
	 * <p>
	 * See https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2
	 * and https://trac.torproject.org/projects/tor/wiki/doc/OONI/censorshipwiki
	 */
	String[] BLOCKED = {"CN", "IR", "EG", "BY", "TR", "SY", "VE", "RU"};

	/**
	 * Countries where obfs4 or meek bridge connections are likely to work.
	 * Should be a subset of {@link #BLOCKED} and the union of
	 * {@link #DEFAULT_OBFS4_BRIDGES}, {@link #NON_DEFAULT_OBFS4_BRIDGES} and
	 * {@link #MEEK_BRIDGES}.
	 */
	String[] BRIDGES = {"CN", "IR", "EG", "BY", "TR", "SY", "VE", "RU"};

	/**
	 * Countries where default obfs4 bridges are likely to work.
	 * Should be a subset of {@link #BRIDGES}.
	 */
	String[] DEFAULT_OBFS4_BRIDGES = {"EG", "BY", "TR", "SY", "VE"};

	/**
	 * Countries where non-default obfs4 bridges are likely to work.
	 * Should be a subset of {@link #BRIDGES}.
	 */
	String[] NON_DEFAULT_OBFS4_BRIDGES = {"RU"};

	/**
	 * Countries where obfs4 bridges won't work and meek is needed.
	 * Should be a subset of {@link #BRIDGES}.
	 */
	String[] MEEK_BRIDGES = {"CN", "IR"};

	/**
	 * Returns true if vanilla Tor connections are blocked in the given country.
	 */
	boolean isTorProbablyBlocked(String countryCode);

	/**
	 * Returns true if bridge connections of some type work in the given
	 * country.
	 */
	boolean doBridgesWork(String countryCode);

	/**
	 * Returns the best type of bridge connection for the given country, or
	 * {@link #DEFAULT_OBFS4_BRIDGES} if no bridge type is known to work.
	 */
	BridgeType getBestBridgeType(String countryCode);

	@IoExecutor
	List<String> getBridges(BridgeType type);

}
