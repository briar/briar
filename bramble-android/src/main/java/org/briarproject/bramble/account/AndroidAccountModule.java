package org.briarproject.bramble.account;

import org.briarproject.bramble.api.account.AccountManager;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AndroidAccountModule {

	@Provides
	@Singleton
	AccountManager provideAccountManager(AndroidAccountManager accountManager) {
		return accountManager;
	}
}
