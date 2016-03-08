package org.briarproject.settings;

import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.settings.SettingsManager;
import org.briarproject.db.DatabaseModule;

import dagger.Module;
import dagger.Provides;

@Module
public class SettingsModule {

	@Provides
	SettingsManager provideSettingsManager(DatabaseComponent db) {
		return new SettingsManagerImpl(db);
	}

}
