package org.briarproject;

import com.google.inject.AbstractModule;

import org.briarproject.api.db.DatabaseConfig;
import org.briarproject.api.system.FileUtils;

import java.io.File;

public class TestDatabaseModule extends AbstractModule {

	private final DatabaseConfig config;

	public TestDatabaseModule() {
		this(new File("."), Long.MAX_VALUE);
	}

	public TestDatabaseModule(File dir) {
		this(dir, Long.MAX_VALUE);
	}

	public TestDatabaseModule(File dir, long maxSize) {
		this.config = new TestDatabaseConfig(dir, maxSize);
	}

	protected void configure() {
		bind(DatabaseConfig.class).toInstance(config);
		bind(FileUtils.class).to(TestFileUtils.class);
	}
}
