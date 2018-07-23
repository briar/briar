package org.briarproject.bramble.api.db;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;

@NotNullByDefault
public interface DatabaseConfig {

	boolean databaseExists();

	File getDatabaseDirectory();

	File getDatabaseKeyDirectory();

	long getMaxSize();
}
