package org.briarproject.bramble.test;

import org.briarproject.bramble.api.system.SecureRandomProvider;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class TestSeedProviderModule {

	@Provides
	@Singleton
	SecureRandomProvider provideSeedProvider() {
		return new TestSecureRandomProvider();
	}
}
