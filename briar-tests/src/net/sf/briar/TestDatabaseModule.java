package net.sf.briar;

import java.io.File;

import net.sf.briar.api.db.DatabaseConfig;
import net.sf.briar.api.os.FileUtils;

import com.google.inject.AbstractModule;

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
