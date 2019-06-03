package org.briarproject.bramble.test;

import org.briarproject.bramble.api.db.DatabaseConfig;

import java.io.File;

import dagger.Module;
import dagger.Provides;

@Module
public class TestDatabaseConfigModule {

	private final DatabaseConfig config;

	public TestDatabaseConfigModule() {
		this(new File("."));
	}

	public TestDatabaseConfigModule(File dir) {
		config = new TestDatabaseConfig(dir);
	}

	@Provides
	DatabaseConfig provideDatabaseConfig() {
		return config;
	}
}
