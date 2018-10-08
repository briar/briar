package org.briarproject.briar.logging;

import org.briarproject.briar.api.logging.PersistentLogManager;

import java.util.logging.Formatter;

import dagger.Module;
import dagger.Provides;

@Module
public class LoggingModule {

	@Provides
	Formatter provideFormatter() {
		return new BriefLogFormatter();
	}

	@Provides
	PersistentLogManager providePersistentLogManager(
			PersistentLogManagerImpl logManager) {
		return logManager;
	}
}
