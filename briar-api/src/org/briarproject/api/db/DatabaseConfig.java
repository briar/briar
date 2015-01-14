package org.briarproject.api.db;

import java.io.File;

import org.briarproject.api.crypto.SecretKey;

public interface DatabaseConfig {

	boolean databaseExists();

	File getDatabaseDirectory();

	void setEncryptionKey(SecretKey key);

	SecretKey getEncryptionKey();

	long getMaxSize();
}
