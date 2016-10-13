package org.briarproject;

import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.db.DatabaseConfig;

import java.io.File;

public class TestDatabaseConfig implements DatabaseConfig {

	private final File dir;
	private final long maxSize;
	private volatile SecretKey key = new SecretKey(new byte[SecretKey.LENGTH]);

	public TestDatabaseConfig(File dir, long maxSize) {
		this.dir = dir;
		this.maxSize = maxSize;
	}

	@Override
	public boolean databaseExists() {
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
	public SecretKey getEncryptionKey() {
		return key;
	}

	@Override
	public void setAuthorNick(String nickName) {

	}

	@Override
	public String getAuthorNick() {
		return null;
	}

	@Override
	public long getMaxSize() {
		return maxSize;
	}
}
