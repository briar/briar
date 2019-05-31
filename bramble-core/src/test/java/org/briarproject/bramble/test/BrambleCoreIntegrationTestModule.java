package org.briarproject.bramble.test;

import org.briarproject.bramble.battery.DefaultBatteryManagerModule;
import org.briarproject.bramble.event.DefaultEventExecutorModule;

import dagger.Module;

@Module(includes = {
		DefaultBatteryManagerModule.class,
		DefaultEventExecutorModule.class,
		TestDatabaseConfigModule.class,
		TestPluginConfigModule.class,
		TestSecureRandomModule.class
})
public class BrambleCoreIntegrationTestModule {
}
