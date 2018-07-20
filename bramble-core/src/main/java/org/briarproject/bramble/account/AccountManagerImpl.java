package org.briarproject.bramble.account;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.db.DatabaseConfig;

import javax.inject.Inject;

class AccountManagerImpl implements AccountManager {

	private final DatabaseConfig databaseConfig;

	@Inject
	AccountManagerImpl(DatabaseConfig databaseConfig) {
		this.databaseConfig = databaseConfig;
	}

	@Override
	public boolean hasDatabaseKey() {
		return databaseConfig.getEncryptionKey() != null;
	}
}
