package org.briarproject.introduction;

import org.briarproject.TestDatabaseModule;
import org.briarproject.TestPluginsModule;
import org.briarproject.TestSeedProviderModule;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.introduction.IntroductionManager;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.clients.ClientsModule;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.data.DataModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.event.EventModule;
import org.briarproject.identity.IdentityModule;
import org.briarproject.lifecycle.LifecycleModule;
import org.briarproject.properties.PropertiesModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.system.SystemModule;
import org.briarproject.transport.TransportModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		TestDatabaseModule.class,
		TestPluginsModule.class,
		TestSeedProviderModule.class,
		LifecycleModule.class,
		IntroductionModule.class,
		DatabaseModule.class,
		CryptoModule.class,
		EventModule.class,
		ContactModule.class,
		IdentityModule.class,
		TransportModule.class,
		ClientsModule.class,
		SyncModule.class,
		SystemModule.class,
		DataModule.class,
		PropertiesModule.class
})
public interface IntroductionIntegrationTestComponent {

	void inject(IntroductionIntegrationTest testCase);

	void inject(ContactModule.EagerSingletons init);

	void inject(CryptoModule.EagerSingletons init);

	void inject(IntroductionModule.EagerSingletons init);

	void inject(LifecycleModule.EagerSingletons init);

	void inject(PropertiesModule.EagerSingletons init);

	void inject(SyncModule.EagerSingletons init);

	void inject(SystemModule.EagerSingletons init);

	void inject(TransportModule.EagerSingletons init);

	LifecycleManager getLifecycleManager();

	EventBus getEventBus();

	IdentityManager getIdentityManager();

	ContactManager getContactManager();

	IntroductionManager getIntroductionManager();

	TransportPropertyManager getTransportPropertyManager();

	SyncSessionFactory getSyncSessionFactory();

	/* the following methods are only needed to manually construct messages */

	DatabaseComponent getDatabaseComponent();

	ClientHelper getClientHelper();

	MessageSender getMessageSender();

	IntroductionGroupFactory getIntroductionGroupFactory();
}
