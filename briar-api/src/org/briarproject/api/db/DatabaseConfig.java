package org.briarproject.api.db;

import java.io.File;

public interface DatabaseConfig {

	boolean databaseExists();

	File getDatabaseDirectory();

	void setEncryptionKey(byte[] key);

	byte[] getEncryptionKey();

	long getMaxSize();
}
