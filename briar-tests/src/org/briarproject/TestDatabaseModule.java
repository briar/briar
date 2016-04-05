package org.briarproject;

import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.db.DatabaseModule;

import java.io.File;
import java.util.concurrent.Executor;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class TestDatabaseModule extends DatabaseModule {

	private final DatabaseConfig config;

	public TestDatabaseModule() {
		this(new File("."));
	}

	public TestDatabaseModule(File dir) {
		config = new TestDatabaseConfig(dir, Long.MAX_VALUE);
	}

	@Provides
	DatabaseConfig provideDatabaseConfig() {
		return config;
	}

	@Provides
	@Singleton
	@DatabaseExecutor
	Executor provideDatabaseExecutor() {
		return new ImmediateExecutor();
	}
}
