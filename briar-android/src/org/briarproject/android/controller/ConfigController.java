package org.briarproject.android.controller;

public interface ConfigController {
	String getEncryptedDatabaseKey();

	void clearPrefs();

	boolean initialized();
}
