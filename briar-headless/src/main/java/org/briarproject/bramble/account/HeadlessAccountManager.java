package org.briarproject.bramble.account;

import org.briarproject.bramble.api.account.AccountManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.identity.IdentityManager;

import javax.inject.Inject;

class HeadlessAccountManager extends AccountManagerImpl
		implements AccountManager {

	@Inject
	HeadlessAccountManager(DatabaseConfig databaseConfig,
			CryptoComponent crypto, IdentityManager identityManager) {
		super(databaseConfig, crypto, identityManager);
	}

}
