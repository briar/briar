package org.briarproject.bramble.test;

import org.briarproject.bramble.api.crypto.CryptoExecutor;

import java.util.concurrent.Executor;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class TestCryptoExecutorModule {

	@Provides
	@Singleton
	@CryptoExecutor
	Executor provideCryptoExecutor() {
		return new ImmediateExecutor();
	}
}
