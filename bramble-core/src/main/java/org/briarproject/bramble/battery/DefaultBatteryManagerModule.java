package org.briarproject.bramble.battery;

import org.briarproject.bramble.api.battery.BatteryManager;

import dagger.Module;
import dagger.Provides;

/**
 * Provides a default implementation of {@link BatteryManager} for systems
 * without batteries.
 */
@Module
public class DefaultBatteryManagerModule {

	@Provides
	BatteryManager provideBatteryManager() {
		return () -> false;
	}
}
