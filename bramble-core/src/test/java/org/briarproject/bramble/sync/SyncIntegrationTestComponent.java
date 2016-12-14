package org.briarproject.bramble.sync;

import org.briarproject.bramble.crypto.CryptoModule;
import org.briarproject.bramble.test.TestSeedProviderModule;
import org.briarproject.bramble.transport.TransportModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		TestSeedProviderModule.class,
		CryptoModule.class,
		SyncModule.class,
		TransportModule.class
})
interface SyncIntegrationTestComponent {

	void inject(SyncIntegrationTest testCase);
}
