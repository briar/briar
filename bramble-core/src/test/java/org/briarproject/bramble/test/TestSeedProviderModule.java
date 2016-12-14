package org.briarproject.bramble.test;

import org.briarproject.bramble.api.system.SeedProvider;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class TestSeedProviderModule {

	@Provides
	@Singleton
	SeedProvider provideSeedProvider() {
		return new TestSeedProvider();
	}
}
