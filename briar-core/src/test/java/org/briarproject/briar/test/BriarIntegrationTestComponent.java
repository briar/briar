package org.briarproject.briar.test;

import org.briarproject.bramble.BrambleCoreEagerSingletons;
import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.plugin.ConnectionManager;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.test.BrambleCoreIntegrationTestModule;
import org.briarproject.briar.api.blog.BlogFactory;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogSharingManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.forum.ForumManager;
import org.briarproject.briar.api.forum.ForumSharingManager;
import org.briarproject.briar.api.introduction.IntroductionManager;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessageFactory;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationManager;
import org.briarproject.briar.blog.BlogModule;
import org.briarproject.briar.client.BriarClientModule;
import org.briarproject.briar.forum.ForumModule;
import org.briarproject.briar.introduction.IntroductionModule;
import org.briarproject.briar.messaging.MessagingModule;
import org.briarproject.briar.privategroup.PrivateGroupModule;
import org.briarproject.briar.privategroup.invitation.GroupInvitationModule;
import org.briarproject.briar.sharing.SharingModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		BrambleCoreIntegrationTestModule.class,
		BrambleCoreModule.class,
		BlogModule.class,
		BriarClientModule.class,
		ForumModule.class,
		GroupInvitationModule.class,
		IntroductionModule.class,
		MessagingModule.class,
		PrivateGroupModule.class,
		SharingModule.class
})
public interface BriarIntegrationTestComponent
		extends BrambleCoreEagerSingletons {

	void inject(BriarIntegrationTest<BriarIntegrationTestComponent> init);

	void inject(BlogModule.EagerSingletons init);

	void inject(ForumModule.EagerSingletons init);

	void inject(GroupInvitationModule.EagerSingletons init);

	void inject(IntroductionModule.EagerSingletons init);

	void inject(MessagingModule.EagerSingletons init);

	void inject(PrivateGroupModule.EagerSingletons init);

	void inject(SharingModule.EagerSingletons init);

	default void injectBriarEagerSingletons() {
		injectBrambleCoreEagerSingletons();
		inject(new BlogModule.EagerSingletons());
		inject(new ForumModule.EagerSingletons());
		inject(new GroupInvitationModule.EagerSingletons());
		inject(new IntroductionModule.EagerSingletons());
		inject(new MessagingModule.EagerSingletons());
		inject(new PrivateGroupModule.EagerSingletons());
		inject(new SharingModule.EagerSingletons());
	}

	LifecycleManager getLifecycleManager();

	EventBus getEventBus();

	IdentityManager getIdentityManager();

	ClientHelper getClientHelper();

	ContactManager getContactManager();

	ConversationManager getConversationManager();

	DatabaseComponent getDatabaseComponent();

	BlogManager getBlogManager();

	BlogSharingManager getBlogSharingManager();

	ForumSharingManager getForumSharingManager();

	ForumManager getForumManager();

	GroupInvitationManager getGroupInvitationManager();

	IntroductionManager getIntroductionManager();

	MessageTracker getMessageTracker();

	MessagingManager getMessagingManager();

	PrivateGroupManager getPrivateGroupManager();

	PrivateMessageFactory getPrivateMessageFactory();

	TransportPropertyManager getTransportPropertyManager();

	AuthorFactory getAuthorFactory();

	BlogFactory getBlogFactory();

	ConnectionManager getConnectionManager();
}
