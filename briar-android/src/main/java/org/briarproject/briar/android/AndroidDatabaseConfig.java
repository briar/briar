package org.briarproject.briar.android;

import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;

@NotNullByDefault
class AndroidDatabaseConfig implements DatabaseConfig {

	private final File dbDir, keyDir;

	AndroidDatabaseConfig(File dbDir, File keyDir) {
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
