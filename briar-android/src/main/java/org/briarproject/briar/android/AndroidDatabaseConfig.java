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

	private final File dir;

	@Nullable
	private volatile SecretKey key = null;
	@Nullable
	private volatile String nickname = null;

	AndroidDatabaseConfig(File dir) {
		this.dir = dir;
	}

	@Override
	public boolean databaseExists() {
		// FIXME should not run on UiThread #620
		if (!dir.isDirectory()) {
			if (LOG.isLoggable(INFO))
				LOG.info(dir.getAbsolutePath() + " is not a directory");
			return false;
		}
		File[] files = dir.listFiles();
		if (LOG.isLoggable(INFO)) {
			if (files == null) {
				LOG.info("Could not list files in " + dir.getAbsolutePath());
			} else {
				LOG.info("Files in " + dir.getAbsolutePath() + ":");
				for (File f : files) LOG.info(f.getName());
			}
			LOG.info("Database exists: " + (files != null && files.length > 0));
		}
		return files != null && files.length > 0;
	}

	@Override
	public File getDatabaseDirectory() {
		File dir = this.dir;
		if (LOG.isLoggable(INFO))
			LOG.info("Database directory: " + dir.getAbsolutePath());
		return dir;
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
