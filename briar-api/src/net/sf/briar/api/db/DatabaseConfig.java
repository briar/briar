package net.sf.briar.api.db;

import java.io.File;

public interface DatabaseConfig {

	File getDataDirectory();

	char[] getPassword();

	long getMaxSize();
}
