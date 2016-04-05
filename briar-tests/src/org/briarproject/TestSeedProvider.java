package org.briarproject;

import org.briarproject.api.system.SeedProvider;

public class TestSeedProvider implements SeedProvider {

	public byte[] getSeed() {
		return TestUtils.getRandomBytes(32);
	}
}
