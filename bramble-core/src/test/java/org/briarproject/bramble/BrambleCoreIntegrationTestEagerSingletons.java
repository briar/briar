package org.briarproject.bramble;

import org.briarproject.bramble.system.DefaultTaskSchedulerModule;

public interface BrambleCoreIntegrationTestEagerSingletons
		extends BrambleCoreEagerSingletons {

	void inject(DefaultTaskSchedulerModule.EagerSingletons init);

	class Helper {

		public static void injectEagerSingletons(
				BrambleCoreIntegrationTestEagerSingletons c) {
			BrambleCoreEagerSingletons.Helper.injectEagerSingletons(c);
			c.inject(new DefaultTaskSchedulerModule.EagerSingletons());
		}
	}
}
