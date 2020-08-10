package org.briarproject.bramble.test;

import org.briarproject.bramble.api.FeatureFlags;
import org.briarproject.bramble.battery.DefaultBatteryManagerModule;
import org.briarproject.bramble.event.DefaultEventExecutorModule;
import org.briarproject.bramble.system.DefaultTaskSchedulerModule;

import dagger.Module;
import dagger.Provides;

@Module(includes = {
		DefaultBatteryManagerModule.class,
		DefaultEventExecutorModule.class,
		DefaultTaskSchedulerModule.class,
		TestDatabaseConfigModule.class,
		TestPluginConfigModule.class,
		TestSecureRandomModule.class
})
public class BrambleCoreIntegrationTestModule {

	@Provides
	FeatureFlags provideFeatureFlags() {
		return () -> true;
	}
}
