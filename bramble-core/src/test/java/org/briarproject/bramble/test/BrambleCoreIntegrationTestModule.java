package org.briarproject.bramble.test;

import org.briarproject.bramble.battery.DefaultBatteryManagerModule;
import org.briarproject.bramble.event.DefaultEventExecutorModule;
import org.briarproject.bramble.system.DefaultWakefulIoExecutorModule;
import org.briarproject.bramble.system.TimeTravelModule;

import dagger.Module;

@Module(includes = {
		DefaultBatteryManagerModule.class,
		DefaultEventExecutorModule.class,
		DefaultWakefulIoExecutorModule.class,
		TestThreadFactoryModule.class,
		TestDatabaseConfigModule.class,
		TestFeatureFlagModule.class,
		TestMailboxDirectoryModule.class,
		TestSecureRandomModule.class,
		TimeTravelModule.class
})
public class BrambleCoreIntegrationTestModule {

}
