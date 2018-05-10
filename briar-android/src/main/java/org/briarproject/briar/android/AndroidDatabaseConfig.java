package org.briarproject.briar.android;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;

import javax.annotation.Nullable;

@NotNullByDefault
class AndroidDatabaseConfig implements DatabaseConfig {

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
		if (!dir.isDirectory()) return false;
		File[] files = dir.listFiles();
		return files != null && files.length > 0;
	}

	@Override
	public File getDatabaseDirectory() {
		return dir;
	}

	@Override
	public void setEncryptionKey(SecretKey key) {
		this.key = key;
	}

	@Override
	public void setLocalAuthorName(String nickname) {
		this.nickname = nickname;
	}

	@Override
	@Nullable
	public String getLocalAuthorName() {
		return nickname;
	}

	@Override
	@Nullable
	public SecretKey getEncryptionKey() {
		return key;
	}

	@Override
	public long getMaxSize() {
		return Long.MAX_VALUE;
	}
}
