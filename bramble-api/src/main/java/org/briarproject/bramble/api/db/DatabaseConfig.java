package org.briarproject.bramble.api.db;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;

import javax.annotation.Nullable;

@NotNullByDefault
public interface DatabaseConfig {

	boolean databaseExists();

	File getDatabaseDirectory();

	void setEncryptionKey(SecretKey key);

	@Nullable
	SecretKey getEncryptionKey();

	void setLocalAuthorName(String nickname);

	@Nullable
	String getLocalAuthorName();

	long getMaxSize();
}
