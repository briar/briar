package org.briarproject.android.helper;

public interface ConfigHelper {
	String getEncryptedDatabaseKey();

	void clearPrefs();

	boolean initialized();
}
