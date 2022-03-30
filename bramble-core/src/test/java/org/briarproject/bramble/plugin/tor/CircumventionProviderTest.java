package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BLOCKED;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BRIDGES;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.DEFAULT_OBFS4;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.MEEK;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.NON_DEFAULT_OBFS4;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.VANILLA;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.DEFAULT_BRIDGES;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.MEEK_BRIDGES;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.NON_DEFAULT_BRIDGES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CircumventionProviderTest extends BrambleTestCase {

	private final CircumventionProvider provider =
			new CircumventionProviderImpl();

	@Test
	public void testInvariants() {
		Set<String> blocked = new HashSet<>(asList(BLOCKED));
		Set<String> bridges = new HashSet<>(asList(BRIDGES));
		Set<String> defaultBridges = new HashSet<>(asList(DEFAULT_BRIDGES));
		Set<String> nonDefaultBridges =
				new HashSet<>(asList(NON_DEFAULT_BRIDGES));
		Set<String> meekBridges = new HashSet<>(asList(MEEK_BRIDGES));
		// BRIDGES should be a subset of BLOCKED
		assertTrue(blocked.containsAll(bridges));
		// BRIDGES should be the union of the bridge type sets
		Set<String> union = new HashSet<>(defaultBridges);
		union.addAll(nonDefaultBridges);
		union.addAll(meekBridges);
		assertEquals(bridges, union);
		// The bridge type sets should not overlap
		assertEmptyIntersection(defaultBridges, nonDefaultBridges);
		assertEmptyIntersection(defaultBridges, meekBridges);
		assertEmptyIntersection(nonDefaultBridges, meekBridges);
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
		for (String country : MEEK_BRIDGES) {
			assertEquals(singletonList(MEEK),
					provider.getSuitableBridgeTypes(country));
		}
		assertEquals(asList(DEFAULT_OBFS4, VANILLA),
				provider.getSuitableBridgeTypes("ZZ"));
	}

	private <T> void assertEmptyIntersection(Set<T> a, Set<T> b) {
		Set<T> intersection = new HashSet<>(a);
		intersection.retainAll(b);
		assertTrue(intersection.isEmpty());
	}
}
