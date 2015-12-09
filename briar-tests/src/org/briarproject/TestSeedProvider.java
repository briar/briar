package org.briarproject;

import org.briarproject.api.system.SeedProvider;

import java.util.Random;

public class TestSeedProvider implements SeedProvider {

	private final Random random = new Random();

	public byte[] getSeed() {
		byte[] seed = new byte[32];
		random.nextBytes(seed);
		return seed;
	}
}
