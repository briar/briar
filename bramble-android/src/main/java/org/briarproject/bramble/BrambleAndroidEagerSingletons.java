package org.briarproject.bramble;

import org.briarproject.bramble.battery.AndroidBatteryModule;
import org.briarproject.bramble.network.AndroidNetworkModule;
import org.briarproject.bramble.reporting.ReportingModule;

public interface BrambleAndroidEagerSingletons {

	void inject(AndroidBatteryModule.EagerSingletons init);

	void inject(AndroidNetworkModule.EagerSingletons init);

	void inject(ReportingModule.EagerSingletons init);

	class Helper {

		public static void injectEagerSingletons(
				BrambleAndroidEagerSingletons c) {
			c.inject(new AndroidBatteryModule.EagerSingletons());
			c.inject(new AndroidNetworkModule.EagerSingletons());
			c.inject(new ReportingModule.EagerSingletons());
		}
	}
}
