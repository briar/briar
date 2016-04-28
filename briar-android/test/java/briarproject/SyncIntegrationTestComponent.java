package briarproject;

import org.briarproject.TestSystemModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.transport.TransportModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		TestSystemModule.class,
		CryptoModule.class,
		SyncModule.class,
		TransportModule.class
})
public interface SyncIntegrationTestComponent {
	void inject(SyncIntegrationTest testCase);
}
