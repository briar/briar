package org.briarproject;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.SeedProvider;

@NotNullByDefault
public class TestSeedProvider implements SeedProvider {

	@Override
	public byte[] getSeed() {
		return TestUtils.getRandomBytes(32);
	}
}
