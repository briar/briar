package org.briarproject;

import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogSharingManager;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.introduction.IntroductionManager;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.privategroup.PrivateGroupManager;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.blogs.BlogsModule;
import org.briarproject.clients.ClientsModule;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.data.DataModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.event.EventModule;
import org.briarproject.forum.ForumModule;
import org.briarproject.identity.IdentityModule;
import org.briarproject.introduction.IntroductionModule;
import org.briarproject.introduction.MessageSender;
import org.briarproject.lifecycle.LifecycleModule;
import org.briarproject.messaging.MessagingModule;
import org.briarproject.privategroup.PrivateGroupModule;
import org.briarproject.privategroup.invitation.GroupInvitationModule;
import org.briarproject.properties.PropertiesModule;
import org.briarproject.sharing.SharingModule;
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
		BlogsModule.class,
		DataModule.class,
		DatabaseModule.class,
		EventModule.class,
		ForumModule.class,
		GroupInvitationModule.class,
		MessagingModule.class,
		IdentityModule.class,
		IntroductionModule.class,
		LifecycleModule.class,
		PrivateGroupModule.class,
		PropertiesModule.class,
		SharingModule.class,
		SyncModule.class,
		SystemModule.class,
		TransportModule.class
})
public interface BriarIntegrationTestComponent {

	void inject(BriarIntegrationTest init);

	void inject(BlogsModule.EagerSingletons init);

	void inject(ContactModule.EagerSingletons init);

	void inject(CryptoModule.EagerSingletons init);

	void inject(ForumModule.EagerSingletons init);

	void inject(IntroductionModule.EagerSingletons init);

	void inject(LifecycleModule.EagerSingletons init);

	void inject(PrivateGroupModule.EagerSingletons init);

	void inject(PropertiesModule.EagerSingletons init);

	void inject(SharingModule.EagerSingletons init);

	void inject(SyncModule.EagerSingletons init);

	void inject(TransportModule.EagerSingletons init);

	LifecycleManager getLifecycleManager();

	EventBus getEventBus();

	IdentityManager getIdentityManager();

	ClientHelper getClientHelper();

	ContactManager getContactManager();

	SyncSessionFactory getSyncSessionFactory();

	DatabaseComponent getDatabaseComponent();

	BlogManager getBlogManager();

	BlogSharingManager getBlogSharingManager();

	ForumSharingManager getForumSharingManager();

	ForumManager getForumManager();

	IntroductionManager getIntroductionManager();

	MessageTracker getMessageTracker();

	MessageSender getMessageSender();

	MessageQueueManager getMessageQueueManager();

	PrivateGroupManager getPrivateGroupManager();

	TransportPropertyManager getTransportPropertyManager();

}
