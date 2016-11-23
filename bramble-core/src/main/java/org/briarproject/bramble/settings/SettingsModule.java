package org.briarproject.bramble.settings;

import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.settings.SettingsManager;

import dagger.Module;
import dagger.Provides;

@Module
public class SettingsModule {

	@Provides
	SettingsManager provideSettingsManager(DatabaseComponent db) {
		return new SettingsManagerImpl(db);
	}

}
