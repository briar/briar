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
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.DEFAULT_OBFS4_BRIDGES;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.MEEK_BRIDGES;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.NON_DEFAULT_OBFS4_BRIDGES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CircumventionProviderTest extends BrambleTestCase {

	private final CircumventionProvider provider =
			new CircumventionProviderImpl();

	@Test
	public void testInvariants() {
		Set<String> blocked = new HashSet<>(asList(BLOCKED));
		Set<String> bridges = new HashSet<>(asList(BRIDGES));
		Set<String> defaultObfs4Bridges =
				new HashSet<>(asList(DEFAULT_OBFS4_BRIDGES));
		Set<String> nonDefaultObfs4Bridges =
				new HashSet<>(asList(NON_DEFAULT_OBFS4_BRIDGES));
		Set<String> meekBridges = new HashSet<>(asList(MEEK_BRIDGES));
		// BRIDGES should be a subset of BLOCKED
		assertTrue(blocked.containsAll(bridges));
		// BRIDGES should be the union of the bridge type sets
		Set<String> union = new HashSet<>(defaultObfs4Bridges);
		union.addAll(nonDefaultObfs4Bridges);
		union.addAll(meekBridges);
		assertEquals(bridges, union);
		// The bridge type sets should not overlap
		assertEmptyIntersection(defaultObfs4Bridges, nonDefaultObfs4Bridges);
		assertEmptyIntersection(defaultObfs4Bridges, meekBridges);
		assertEmptyIntersection(nonDefaultObfs4Bridges, meekBridges);
	}

	@Test
	public void testGetBestBridgeType() {
		for (String country : DEFAULT_OBFS4_BRIDGES) {
			assertEquals(DEFAULT_OBFS4, provider.getBestBridgeType(country));
		}
		for (String country : NON_DEFAULT_OBFS4_BRIDGES) {
			assertEquals(NON_DEFAULT_OBFS4,
					provider.getBestBridgeType(country));
		}
		for (String country : MEEK_BRIDGES) {
			assertEquals(MEEK, provider.getBestBridgeType(country));
		}
		assertEquals(DEFAULT_OBFS4, provider.getBestBridgeType("ZZ"));
	}

	private <T> void assertEmptyIntersection(Set<T> a, Set<T> b) {
		Set<T> intersection = new HashSet<>(a);
		intersection.retainAll(b);
		assertTrue(intersection.isEmpty());
	}
}
