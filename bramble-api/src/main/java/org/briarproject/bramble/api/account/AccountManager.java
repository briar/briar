package org.briarproject.bramble.api.account;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

@NotNullByDefault
public interface AccountManager {

	boolean hasDatabaseKey();

	@Nullable
	SecretKey getDatabaseKey();

	boolean accountExists();

	boolean createAccount(String password);

	void deleteAccount();

	boolean signIn(String password);

	boolean changePassword(String oldPassword, String newPassword);
}
