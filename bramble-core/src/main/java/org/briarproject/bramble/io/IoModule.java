package org.briarproject.bramble.io;

import org.briarproject.bramble.api.io.TimeoutMonitor;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class IoModule {

	@Provides
	@Singleton
	TimeoutMonitor provideTimeoutMonitor(TimeoutMonitorImpl timeoutMonitor) {
		return timeoutMonitor;
	}
}
