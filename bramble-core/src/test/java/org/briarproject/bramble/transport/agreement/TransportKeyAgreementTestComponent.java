package org.briarproject.bramble.transport.agreement;

import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.mailbox.ModularMailboxModule;
import org.briarproject.bramble.test.BrambleCoreIntegrationTestModule;
import org.briarproject.bramble.test.BrambleIntegrationTestComponent;
import org.briarproject.bramble.test.TestDnsModule;
import org.briarproject.bramble.test.TestPluginConfigModule;
import org.briarproject.bramble.test.TestSocksModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleCoreIntegrationTestModule.class,
		BrambleCoreModule.class,
		ModularMailboxModule.class,
		TestDnsModule.class,
		TestSocksModule.class,
		TestPluginConfigModule.class,
})
interface TransportKeyAgreementTestComponent
		extends BrambleIntegrationTestComponent {

	KeyManager getKeyManager();

	TransportKeyAgreementManagerImpl getTransportKeyAgreementManager();

	ContactManager getContactManager();

	LifecycleManager getLifecycleManager();

	ContactGroupFactory getContactGroupFactory();

	SessionParser getSessionParser();

	TransportPropertyManager getTransportPropertyManager();

	DatabaseComponent getDatabaseComponent();
}
