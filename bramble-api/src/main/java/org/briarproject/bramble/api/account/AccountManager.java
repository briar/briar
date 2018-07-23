package org.briarproject.bramble.api.account;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

@NotNullByDefault
public interface AccountManager {

	boolean hasDatabaseKey();

	@Nullable
	SecretKey getDatabaseKey();

	void setDatabaseKey(SecretKey k);

	@Nullable
	String getEncryptedDatabaseKey();

	boolean storeEncryptedDatabaseKey(String hex);
}
