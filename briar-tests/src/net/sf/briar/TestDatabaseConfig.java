package net.sf.briar;

import java.io.File;

import net.sf.briar.api.db.DatabaseConfig;

public class TestDatabaseConfig implements DatabaseConfig {

	private final File dir;
	private final long maxSize;
	private volatile byte[] key = new byte[] { 'f', 'o', 'o' };

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

	public void setEncryptionKey(byte[] key) {
		this.key = key;
	}

	public byte[] getEncryptionKey() {
		return key;
	}

	public long getMaxSize() {
		return maxSize;
	}
}
