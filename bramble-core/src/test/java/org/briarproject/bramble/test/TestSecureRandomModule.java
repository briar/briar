package org.briarproject.bramble.test;

import org.briarproject.bramble.api.system.SecureRandomProvider;

import dagger.Module;
import dagger.Provides;

@Module
public class TestSecureRandomModule {

	@Provides
	SecureRandomProvider provideSecureRandomProvider() {
		return new TestSecureRandomProvider();
	}
}
