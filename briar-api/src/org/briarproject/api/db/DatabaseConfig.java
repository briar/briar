package org.briarproject.api.db;

import org.briarproject.api.crypto.SecretKey;

import java.io.File;

public interface DatabaseConfig {

	boolean databaseExists();

	File getDatabaseDirectory();

	void setEncryptionKey(SecretKey key);

	SecretKey getEncryptionKey();

	void setAuthorNick(String nickname);

	String getAuthorNick();

	long getMaxSize();
}
