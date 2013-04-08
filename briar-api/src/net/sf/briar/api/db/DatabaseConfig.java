package net.sf.briar.api.db;

import java.io.File;

public interface DatabaseConfig {

	boolean databaseExists();

	File getDatabaseDirectory();

	char[] getPassword();

	long getMaxSize();
}
