package org.briarproject.api.db;

import org.briarproject.api.crypto.SecretKey;

import java.io.File;

public interface DatabaseConfig {

	boolean databaseExists();

	File getDatabaseDirectory();

	void setEncryptionKey(SecretKey key);

	SecretKey getEncryptionKey();

	void setLocalAuthorName(String nickname);

	String getLocalAuthorName();

	long getMaxSize();
}
