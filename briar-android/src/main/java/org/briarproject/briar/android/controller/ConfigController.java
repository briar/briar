package org.briarproject.briar.android.controller;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

@NotNullByDefault
public interface ConfigController {

	@Nullable
	String getEncryptedDatabaseKey();

	boolean storeEncryptedDatabaseKey(String hex);

	void deleteAccount();

	boolean accountExists();

	boolean accountSignedIn();

}
