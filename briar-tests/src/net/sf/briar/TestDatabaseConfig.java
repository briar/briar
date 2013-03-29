package net.sf.briar;

import java.io.File;

import net.sf.briar.api.db.DatabaseConfig;

public class TestDatabaseConfig implements DatabaseConfig {

	private final File dir;
	private final long maxSize;

	public TestDatabaseConfig(File dir, long maxSize) {
		this.dir = dir;
		this.maxSize = maxSize;
	}

	public File getDataDirectory() {
		return dir;
	}

	public char[] getPassword() {
		return "foo bar".toCharArray();
	}

	public long getMaxSize() {
		return maxSize;
	}
}
