package org.briarproject.bramble.logging;

import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.logging.PersistentLogManager;

import java.util.logging.Formatter;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class LoggingModule {

	@Provides
	Formatter provideFormatter() {
		return new BriefLogFormatter();
	}

	@Provides
	@Singleton
	PersistentLogManager providePersistentLogManager(
			LifecycleManager lifecycleManager,
			PersistentLogManagerImpl persistentLogManager) {
		lifecycleManager.registerOpenDatabaseHook(persistentLogManager);
		return persistentLogManager;
	}
}
