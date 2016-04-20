package org.briarproject;

import org.briarproject.api.contact.ContactManager;
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
import org.briarproject.introduction.IntroductionModule;
import org.briarproject.lifecycle.LifecycleModule;
import org.briarproject.properties.PropertiesModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.transport.TransportModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		TestSystemModule.class,
		TestDatabaseModule.class,
		TestPluginsModule.class,
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

	void inject(TransportModule.EagerSingletons init);

	LifecycleManager getLifecycleManager();

	EventBus getEventBus();

	IdentityManager getIdentityManager();

	ContactManager getContactManager();

	IntroductionManager getIntroductionManager();

	TransportPropertyManager getTransportPropertyManager();

	SyncSessionFactory getSyncSessionFactory();

}
