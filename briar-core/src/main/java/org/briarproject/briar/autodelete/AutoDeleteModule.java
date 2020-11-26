package org.briarproject.briar.autodelete;

import org.briarproject.briar.api.autodelete.AutoDeleteManager;

import dagger.Module;
import dagger.Provides;

@Module
public class AutoDeleteModule {

	@Provides
	AutoDeleteManager provideAutoDeleteManager(
			AutoDeleteManagerImpl autoDeleteManager) {
		return autoDeleteManager;
	}
}
