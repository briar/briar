package org.briarproject.briar.telegram;

import org.briarproject.bramble.api.FeatureFlags;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.briar.api.telegram.TelegramAuthSession;
import org.briarproject.briar.api.telegram.TelegramConnector;

import java.io.File;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class TelegramModule {

	@Provides
	@Singleton
	TelegramConnector provideTelegramConnector(FeatureFlags featureFlags) {
		if (featureFlags.shouldEnableTelegramConnector()) {
			return new StubTelegramConnector();
		}
		return new NoOpTelegramConnector();
	}

	@Provides
	@Singleton
	TelegramAuthSession provideTelegramAuthSession(FeatureFlags featureFlags,
			DatabaseConfig databaseConfig) {
		if (featureFlags.shouldEnableTelegramConnector()) {
			return new TelegramAuthSessionImpl(
					new StubTelegramTdlibLoginClient(new File(
							databaseConfig.getDatabaseDirectory(), "tdlib")));
		}
		return new TelegramAuthSessionImpl(new NoOpTelegramTdlibLoginClient());
	}
}
