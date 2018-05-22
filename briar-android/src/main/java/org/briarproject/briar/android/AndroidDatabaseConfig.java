package org.briarproject.briar.android;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static java.util.logging.Level.INFO;

@NotNullByDefault
class AndroidDatabaseConfig implements DatabaseConfig {

	private static final Logger LOG =
			Logger.getLogger(AndroidDatabaseConfig.class.getName());

	private final File dbDir, keyDir;

	@Nullable
	private volatile SecretKey key = null;
	@Nullable
	private volatile String nickname = null;

	AndroidDatabaseConfig(File dbDir, File keyDir) {
		this.dbDir = dbDir;
		this.keyDir = keyDir;
	}

	@Override
	public boolean databaseExists() {
		// FIXME should not run on UiThread #620
		if (!dbDir.isDirectory()) {
			if (LOG.isLoggable(INFO))
				LOG.info(dbDir.getAbsolutePath() + " is not a directory");
			return false;
		}
		File[] files = dbDir.listFiles();
		if (LOG.isLoggable(INFO)) {
			if (files == null) {
				LOG.info("Could not list files in " + dbDir.getAbsolutePath());
			} else {
				LOG.info("Files in " + dbDir.getAbsolutePath() + ":");
				for (File f : files) LOG.info(f.getName());
			}
			LOG.info("Database exists: " + (files != null && files.length > 0));
		}
		return files != null && files.length > 0;
	}

	@Override
	public File getDatabaseDirectory() {
		if (LOG.isLoggable(INFO))
			LOG.info("Database directory: " + dbDir.getAbsolutePath());
		return dbDir;
	}

	@Override
	public File getDatabaseKeyDirectory() {
		if (LOG.isLoggable(INFO))
			LOG.info("Database key directory: " + keyDir.getAbsolutePath());
		return keyDir;
	}

	@Override
	public void setEncryptionKey(SecretKey key) {
		LOG.info("Setting database key");
		this.key = key;
	}

	@Override
	public void setLocalAuthorName(String nickname) {
		LOG.info("Setting local author name");
		this.nickname = nickname;
	}

	@Override
	@Nullable
	public String getLocalAuthorName() {
		String nickname = this.nickname;
		if (LOG.isLoggable(INFO))
			LOG.info("Local author name has been set: " + (nickname != null));
		return nickname;
	}

	@Override
	@Nullable
	public SecretKey getEncryptionKey() {
		SecretKey key = this.key;
		if (LOG.isLoggable(INFO))
			LOG.info("Database key has been set: " + (key != null));
		return key;
	}

	@Override
	public long getMaxSize() {
		return Long.MAX_VALUE;
	}
}
