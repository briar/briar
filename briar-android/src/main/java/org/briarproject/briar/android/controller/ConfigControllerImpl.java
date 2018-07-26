package org.briarproject.briar.android.controller;

import android.util.Log;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;

// TODO: Remove this class, which just delegates to AccountManager

@NotNullByDefault
public class ConfigControllerImpl implements ConfigController {

	protected final AccountManager accountManager;

	@Inject
	public ConfigControllerImpl(AccountManager accountManager) {
		// TODO: Remove
		Log.i(getClass().getName(), "Using account manager "
				+ accountManager.getClass().getName());
		this.accountManager = accountManager;
	}

	@Override
	@Nullable
	public String getEncryptedDatabaseKey() {
		return accountManager.getEncryptedDatabaseKey();
	}

	@Override
	public boolean storeEncryptedDatabaseKey(String hex) {
		return accountManager.storeEncryptedDatabaseKey(hex);
	}

	@Override
	public void deleteAccount() {
		accountManager.deleteAccount();
	}

	@Override
	public boolean accountExists() {
		return accountManager.hasDatabaseKey();
	}

	@Override
	public boolean accountSignedIn() {
		return accountManager.hasDatabaseKey();
	}
}
