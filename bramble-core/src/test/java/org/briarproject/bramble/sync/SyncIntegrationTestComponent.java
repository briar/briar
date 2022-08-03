package org.briarproject.bramble.sync;

import org.briarproject.bramble.BrambleCoreIntegrationTestEagerSingletons;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.test.BrambleCoreIntegrationTestModule;
import org.briarproject.bramble.test.TestDnsModule;
import org.briarproject.bramble.test.TestSocksModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleCoreIntegrationTestModule.class,
		BrambleCoreModule.class,
		TestDnsModule.class,
		TestSocksModule.class
})
interface SyncIntegrationTestComponent extends
		BrambleCoreIntegrationTestEagerSingletons {

	void inject(SyncIntegrationTest testCase);
}
