package org.briarproject.bramble.battery;

import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AndroidBatteryModule {

	@Provides
	@Singleton
	BatteryManager provideBatteryManager(LifecycleManager lifecycleManager,
			AndroidBatteryManager batteryManager) {
		lifecycleManager.registerService(batteryManager);
		return batteryManager;
	}
}
