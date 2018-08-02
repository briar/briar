package org.briarproject.bramble.test;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;

@NotNullByDefault
public class TestDatabaseConfig implements DatabaseConfig {

	private final File dbDir, keyDir;
	private final long maxSize;

	public TestDatabaseConfig(File testDir, long maxSize) {
		dbDir = new File(testDir, "db");
		keyDir = new File(testDir, "key");
		this.maxSize = maxSize;
	}

	@Override
	public File getDatabaseDirectory() {
		return dbDir;
	}

	@Override
	public File getDatabaseKeyDirectory() {
		return keyDir;
	}

	@Override
	public long getMaxSize() {
		return maxSize;
	}
}
