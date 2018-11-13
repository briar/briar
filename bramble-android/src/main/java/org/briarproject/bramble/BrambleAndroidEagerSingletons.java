package org.briarproject.bramble;

import org.briarproject.bramble.battery.AndroidBatteryModule;
import org.briarproject.bramble.network.AndroidNetworkModule;

public interface BrambleAndroidEagerSingletons {

	void inject(AndroidBatteryModule.EagerSingletons init);

	void inject(AndroidNetworkModule.EagerSingletons init);
}
