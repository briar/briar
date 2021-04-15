package org.briarproject.bramble;

import org.briarproject.bramble.system.TimeTravelModule;

public interface BrambleCoreIntegrationTestEagerSingletons
		extends BrambleCoreEagerSingletons {

	void inject(TimeTravelModule.EagerSingletons init);

	class Helper {

		public static void injectEagerSingletons(
				BrambleCoreIntegrationTestEagerSingletons c) {
			BrambleCoreEagerSingletons.Helper.injectEagerSingletons(c);
			c.inject(new TimeTravelModule.EagerSingletons());
		}
	}
}
