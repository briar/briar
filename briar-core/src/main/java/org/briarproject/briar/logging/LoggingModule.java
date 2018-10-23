package org.briarproject.briar.logging;

import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.briar.api.logging.PersistentLogManager;

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
