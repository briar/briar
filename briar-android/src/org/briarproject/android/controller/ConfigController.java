package org.briarproject.android.controller;

import android.content.Context;

public interface ConfigController {

	String getEncryptedDatabaseKey();

	void setEncryptedDatabaseKey(String hex);

	void deleteAccount(Context ctx);

	boolean accountExists();
}
