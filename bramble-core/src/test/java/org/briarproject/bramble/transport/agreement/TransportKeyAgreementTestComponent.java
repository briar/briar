package org.briarproject.bramble.transport.agreement;

import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.test.BrambleCoreIntegrationTestModule;
import org.briarproject.bramble.test.BrambleIntegrationTestComponent;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleCoreIntegrationTestModule.class,
		BrambleCoreModule.class
})
interface TransportKeyAgreementTestComponent
		extends BrambleIntegrationTestComponent {

	KeyManager getKeyManager();

	TransportKeyAgreementManagerImpl getTransportKeyAgreementManager();

	ContactManager getContactManager();

	LifecycleManager getLifecycleManager();

	ContactGroupFactory getContactGroupFactory();

	SessionParser getSessionParser();
}
