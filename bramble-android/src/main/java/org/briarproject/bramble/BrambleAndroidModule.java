package org.briarproject.bramble;

import org.briarproject.bramble.battery.AndroidBatteryModule;
import org.briarproject.bramble.network.AndroidNetworkModule;
import org.briarproject.bramble.plugin.tor.CircumventionModule;
import org.briarproject.bramble.reporting.ReportingModule;
import org.briarproject.bramble.socks.SocksModule;
import org.briarproject.bramble.system.AndroidSystemModule;
import org.briarproject.bramble.system.AndroidTaskSchedulerModule;
import org.briarproject.bramble.system.AndroidWakefulIoExecutorModule;

import dagger.Module;

@Module(includes = {
		AndroidBatteryModule.class,
		AndroidNetworkModule.class,
		AndroidSystemModule.class,
		AndroidTaskSchedulerModule.class,
		AndroidWakefulIoExecutorModule.class,
		CircumventionModule.class,
		ReportingModule.class,
		SocksModule.class
})
public class BrambleAndroidModule {
}
