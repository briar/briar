package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BLOCKED;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BRIDGES;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.DEFAULT_OBFS4;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.MEEK;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.NON_DEFAULT_OBFS4;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.SNOWFLAKE;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.VANILLA;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.DEFAULT_BRIDGES;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.DPI_BRIDGES;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.NON_DEFAULT_BRIDGES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class CircumventionProviderImplTest extends BrambleTestCase {

	private final CircumventionProviderImpl provider =
			new CircumventionProviderImpl();

	@Test
	public void testInvariants() {
		Set<String> blocked = new HashSet<>(asList(BLOCKED));
		Set<String> bridges = new HashSet<>(asList(BRIDGES));
		Set<String> defaultBridges = new HashSet<>(asList(DEFAULT_BRIDGES));
		Set<String> nonDefaultBridges =
				new HashSet<>(asList(NON_DEFAULT_BRIDGES));
		Set<String> dpiBridges = new HashSet<>(asList(DPI_BRIDGES));
		// BRIDGES should be a subset of BLOCKED
		assertTrue(blocked.containsAll(bridges));
		// BRIDGES should be the union of the bridge type sets
		Set<String> union = new HashSet<>(defaultBridges);
		union.addAll(nonDefaultBridges);
		union.addAll(dpiBridges);
		assertEquals(bridges, union);
		// The bridge type sets should not overlap
		assertEmptyIntersection(defaultBridges, nonDefaultBridges);
		assertEmptyIntersection(defaultBridges, dpiBridges);
		assertEmptyIntersection(nonDefaultBridges, dpiBridges);
	}

	@Test
	public void testGetBestBridgeType() {
		for (String country : DEFAULT_BRIDGES) {
			assertEquals(asList(DEFAULT_OBFS4, VANILLA),
					provider.getSuitableBridgeTypes(country));
		}
		for (String country : NON_DEFAULT_BRIDGES) {
			assertEquals(asList(NON_DEFAULT_OBFS4, VANILLA),
					provider.getSuitableBridgeTypes(country));
		}
		for (String country : DPI_BRIDGES) {
			assertEquals(asList(NON_DEFAULT_OBFS4, MEEK, SNOWFLAKE),
					provider.getSuitableBridgeTypes(country));
		}
		assertEquals(asList(DEFAULT_OBFS4, VANILLA),
				provider.getSuitableBridgeTypes("ZZ"));
	}

	@Test
	public void testHasSnowflakeParamsWithLetsEncrypt() {
		testHasSnowflakeParams(true);
	}

	@Test
	public void testHasSnowflakeParamsWithoutLetsEncrypt() {
		testHasSnowflakeParams(false);
	}

	private void testHasSnowflakeParams(boolean letsEncrypt) {
		String tmParams = provider.getSnowflakeParams("TM", letsEncrypt);
		String defaultParams = provider.getSnowflakeParams("ZZ", letsEncrypt);
		assertFalse(tmParams.isEmpty());
		assertFalse(defaultParams.isEmpty());
		assertNotEquals(defaultParams, tmParams);
	}

	private <T> void assertEmptyIntersection(Set<T> a, Set<T> b) {
		Set<T> intersection = new HashSet<>(a);
		intersection.retainAll(b);
		assertTrue(intersection.isEmpty());
	}
}
