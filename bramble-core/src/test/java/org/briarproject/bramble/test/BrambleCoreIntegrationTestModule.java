package org.briarproject.bramble.test;

import org.briarproject.bramble.api.FeatureFlags;
import org.briarproject.bramble.battery.DefaultBatteryManagerModule;
import org.briarproject.bramble.event.DefaultEventExecutorModule;
import org.briarproject.bramble.system.DefaultTaskSchedulerModule;
import org.briarproject.bramble.system.DefaultWakefulIoExecutorModule;

import dagger.Module;
import dagger.Provides;

@Module(includes = {
		DefaultBatteryManagerModule.class,
		DefaultEventExecutorModule.class,
		DefaultTaskSchedulerModule.class,
		DefaultWakefulIoExecutorModule.class,
		TestClockModule.class,
		TestDatabaseConfigModule.class,
		TestPluginConfigModule.class,
		TestSecureRandomModule.class
})
public class BrambleCoreIntegrationTestModule {

	@Provides
	FeatureFlags provideFeatureFlags() {
		return new FeatureFlags() {

			@Override
			public boolean shouldEnableImageAttachments() {
				return true;
			}

			@Override
			public boolean shouldEnableProfilePictures() {
				return true;
			}
		};
	}
}
