package org.briarproject.briar.android;

import org.briarproject.bramble.api.crypto.KeyStoreConfig;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;

import javax.annotation.Nullable;

import static android.os.Build.VERSION.SDK_INT;

@NotNullByDefault
class AndroidDatabaseConfig implements DatabaseConfig {

	private final File dbDir, keyDir;
	@Nullable
	private final KeyStoreConfig keyStoreConfig;

	AndroidDatabaseConfig(File dbDir, File keyDir) {
		this.dbDir = dbDir;
		this.keyDir = keyDir;
		keyStoreConfig = SDK_INT >= 23 ? new AndroidKeyStoreConfig() : null;
	}

	@Override
	public File getDatabaseDirectory() {
		return dbDir;
	}

	@Override
	public File getDatabaseKeyDirectory() {
		return keyDir;
	}

	@Nullable
	@Override
	public KeyStoreConfig getKeyStoreConfig() {
		return keyStoreConfig;
	}
}
