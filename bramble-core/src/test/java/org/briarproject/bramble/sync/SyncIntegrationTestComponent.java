package org.briarproject.bramble.sync;

import org.briarproject.bramble.crypto.CryptoModule;
import org.briarproject.bramble.record.RecordModule;
import org.briarproject.bramble.system.SystemModule;
import org.briarproject.bramble.test.TestSecureRandomModule;
import org.briarproject.bramble.transport.TransportModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		TestSecureRandomModule.class,
		CryptoModule.class,
		RecordModule.class,
		SyncModule.class,
		SystemModule.class,
		TransportModule.class
})
interface SyncIntegrationTestComponent {

	void inject(SyncIntegrationTest testCase);
}
