package org.briarproject.bramble.test;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;

@NotNullByDefault
public class TestDatabaseConfig implements DatabaseConfig {

	private final File dbDir, keyDir;
	private final long maxSize;
	private volatile SecretKey key = new SecretKey(new byte[SecretKey.LENGTH]);

	public TestDatabaseConfig(File testDir, long maxSize) {
		dbDir = new File(testDir, "db");
		keyDir = new File(testDir, "key");
		this.maxSize = maxSize;
	}

	@Override
	public boolean databaseExists() {
		if (!dbDir.isDirectory()) return false;
		File[] files = dbDir.listFiles();
		return files != null && files.length > 0;
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
	public void setEncryptionKey(SecretKey key) {
		this.key = key;
	}

	@Override
	public SecretKey getEncryptionKey() {
		return key;
	}

	@Override
	public void setLocalAuthorName(String nickname) {

	}

	@Override
	public String getLocalAuthorName() {
		return null;
	}

	@Override
	public long getMaxSize() {
		return maxSize;
	}
}
