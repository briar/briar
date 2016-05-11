package org.briarproject;

import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.clients.PrivateGroupFactory;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.clients.ClientsModule;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.data.DataModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.event.EventModule;
import org.briarproject.forum.ForumModule;
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
		ClientsModule.class,
		ContactModule.class,
		CryptoModule.class,
		DataModule.class,
		DatabaseModule.class,
		EventModule.class,
		ForumModule.class,
		IdentityModule.class,
		LifecycleModule.class,
		PropertiesModule.class,
		SyncModule.class,
		SystemModule.class,
		TransportModule.class
})
public interface ForumSharingIntegrationTestComponent {

	void inject(ForumSharingIntegrationTest testCase);

	void inject(ContactModule.EagerSingletons init);

	void inject(CryptoModule.EagerSingletons init);

	void inject(ForumModule.EagerSingletons init);

	void inject(LifecycleModule.EagerSingletons init);

	void inject(PropertiesModule.EagerSingletons init);

	void inject(SyncModule.EagerSingletons init);

	void inject(TransportModule.EagerSingletons init);

	LifecycleManager getLifecycleManager();

	EventBus getEventBus();

	IdentityManager getIdentityManager();

	ContactManager getContactManager();

	ForumSharingManager getForumSharingManager();

	ForumManager getForumManager();

	SyncSessionFactory getSyncSessionFactory();

	/* the following methods are only needed to manually construct messages */

	DatabaseComponent getDatabaseComponent();

	PrivateGroupFactory getPrivateGroupFactory();

	ClientHelper getClientHelper();

	MessageQueueManager getMessageQueueManager();

}
