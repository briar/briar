package net.sf.briar.api.db;

import java.io.File;

import net.sf.briar.api.crypto.Password;

public interface DatabaseConfig {

	File getDataDirectory();

	Password getPassword();

	long getMaxSize();
}
