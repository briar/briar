package org.briarproject.briar.headless;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;

@NotNullByDefault
class HeadlessDatabaseConfig implements DatabaseConfig {

	private final File dbDir, keyDir;

	HeadlessDatabaseConfig(File dbDir, File keyDir) {
		this.dbDir = dbDir;
		this.keyDir = keyDir;
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
		return Long.MAX_VALUE;
	}
}
