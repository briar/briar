package org.briarproject;

import java.io.File;

import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseConfig;

public class TestDatabaseConfig implements DatabaseConfig {

	private final File dir;
	private final long maxSize;
	private volatile SecretKey key = new SecretKey(new byte[SecretKey.LENGTH]);

	public TestDatabaseConfig(File dir, long maxSize) {
		this.dir = dir;
		this.maxSize = maxSize;
	}

	public boolean databaseExists() {
		return dir.isDirectory() && dir.listFiles().length > 0;
	}

	public File getDatabaseDirectory() {
		return dir;
	}

	public void setEncryptionKey(SecretKey key) {
		this.key = key;
	}

	public SecretKey getEncryptionKey() {
		return key;
	}

	public long getMaxSize() {
		return maxSize;
	}
}
