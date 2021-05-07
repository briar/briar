package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.plugin.file.RemovableDriveManager;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class RemovableDriveModule {

	@Provides
	@Singleton
	RemovableDriveManager provideRemovableDriveManager(
			RemovableDriveManagerImpl removableDriveManager) {
		return removableDriveManager;
	}
}
