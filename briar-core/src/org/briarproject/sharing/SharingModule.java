package org.briarproject.sharing;

import org.briarproject.api.blogs.BlogManager;
import org.briarproject.api.blogs.BlogSharingManager;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.messaging.ConversationManager;
import org.briarproject.api.system.Clock;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class SharingModule {

	public static class EagerSingletons {
		@Inject
		BlogSharingValidator blogSharingValidator;
		@Inject
		ForumSharingValidator forumSharingValidator;
		@Inject
		ForumSharingManager forumSharingManager;
		@Inject
		BlogSharingManager blogSharingManager;
	}

	@Provides
	@Singleton
	BlogSharingValidator provideBlogSharingValidator(
			MessageQueueManager messageQueueManager, ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {

		BlogSharingValidator
				validator = new BlogSharingValidator(clientHelper,
				metadataEncoder, clock);
		messageQueueManager.registerMessageValidator(
				BlogSharingManagerImpl.CLIENT_ID, validator);

		return validator;
	}

	@Provides
	@Singleton
	BlogSharingManager provideBlogSharingManager(
			LifecycleManager lifecycleManager,
			ContactManager contactManager,
			MessageQueueManager messageQueueManager,
			ConversationManager conversationManager,
			BlogManager blogManager,
			BlogSharingManagerImpl blogSharingManager) {

		lifecycleManager.registerClient(blogSharingManager);
		contactManager.registerAddContactHook(blogSharingManager);
		contactManager.registerRemoveContactHook(blogSharingManager);
		messageQueueManager.registerIncomingMessageHook(
				BlogSharingManagerImpl.CLIENT_ID, blogSharingManager);
		conversationManager.registerConversationClient(blogSharingManager);
		blogManager.registerRemoveBlogHook(blogSharingManager);

		return blogSharingManager;
	}

	@Provides
	@Singleton
	ForumSharingValidator provideForumSharingValidator(
			MessageQueueManager messageQueueManager, ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {

		ForumSharingValidator
				validator = new ForumSharingValidator(clientHelper,
				metadataEncoder, clock);
		messageQueueManager.registerMessageValidator(
				ForumSharingManagerImpl.CLIENT_ID, validator);

		return validator;
	}

	@Provides
	@Singleton
	ForumSharingManager provideForumSharingManager(
			LifecycleManager lifecycleManager,
			ContactManager contactManager,
			MessageQueueManager messageQueueManager,
			ConversationManager conversationManager,
			ForumManager forumManager,
			ForumSharingManagerImpl forumSharingManager) {

		lifecycleManager.registerClient(forumSharingManager);
		contactManager.registerAddContactHook(forumSharingManager);
		contactManager.registerRemoveContactHook(forumSharingManager);
		messageQueueManager.registerIncomingMessageHook(
				ForumSharingManagerImpl.CLIENT_ID, forumSharingManager);
		conversationManager.registerConversationClient(forumSharingManager);
		forumManager.registerRemoveForumHook(forumSharingManager);

		return forumSharingManager;
	}

}
