package org.briarproject.android.controller;

import android.content.Context;

public interface ConfigController {

	String getEncryptedDatabaseKey();

	void storeEncryptedDatabaseKey(String hex);

	void deleteAccount(Context ctx);

	boolean accountExists();
}
