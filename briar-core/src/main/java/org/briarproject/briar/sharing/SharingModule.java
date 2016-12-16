package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.sync.ValidationManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogSharingManager;
import org.briarproject.briar.api.client.MessageQueueManager;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumFactory;
import org.briarproject.briar.api.forum.ForumManager;
import org.briarproject.briar.api.forum.ForumSharingManager;
import org.briarproject.briar.api.messaging.ConversationManager;

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

		BlogSharingValidator validator =
				new BlogSharingValidator(clientHelper, metadataEncoder, clock);
		messageQueueManager.registerMessageValidator(
				BlogSharingManager.CLIENT_ID, validator);

		return validator;
	}

	@Provides
	@Singleton
	BlogSharingManager provideBlogSharingManager(
			LifecycleManager lifecycleManager, ContactManager contactManager,
			MessageQueueManager messageQueueManager,
			ConversationManager conversationManager, BlogManager blogManager,
			BlogSharingManagerImpl blogSharingManager) {

		lifecycleManager.registerClient(blogSharingManager);
		contactManager.registerAddContactHook(blogSharingManager);
		contactManager.registerRemoveContactHook(blogSharingManager);
		messageQueueManager.registerIncomingMessageHook(
				BlogSharingManager.CLIENT_ID, blogSharingManager);
		conversationManager.registerConversationClient(blogSharingManager);
		blogManager.registerRemoveBlogHook(blogSharingManager);

		return blogSharingManager;
	}

	@Provides
	@Singleton
	ForumSharingValidator provideForumSharingValidator(
			ValidationManager validationManager, MessageEncoder messageEncoder,
			ClientHelper clientHelper, MetadataEncoder metadataEncoder,
			Clock clock, ForumFactory forumFactory) {
		ForumSharingValidator validator =
				new ForumSharingValidator(messageEncoder, clientHelper,
						metadataEncoder, clock, forumFactory);
		validationManager
				.registerMessageValidator(ForumSharingManager.CLIENT_ID,
						validator);
		return validator;
	}

	@Provides
	@Singleton
	ForumSharingManager provideForumSharingManager(
			LifecycleManager lifecycleManager, ContactManager contactManager,
			ValidationManager validationManager,
			ConversationManager conversationManager, ForumManager forumManager,
			ForumSharingManagerImpl forumSharingManager) {

		lifecycleManager.registerClient(forumSharingManager);
		contactManager.registerAddContactHook(forumSharingManager);
		contactManager.registerRemoveContactHook(forumSharingManager);
		validationManager.registerIncomingMessageHook(
				ForumSharingManager.CLIENT_ID, forumSharingManager);
		conversationManager.registerConversationClient(forumSharingManager);
		forumManager.registerRemoveForumHook(forumSharingManager);

		return forumSharingManager;
	}

	@Provides
	MessageEncoder provideMessageEncoder(MessageEncoderImpl messageEncoder) {
		return messageEncoder;
	}

	@Provides
	MessageParser<Forum> provideForumMessageParser(
			ForumMessageParserImpl forumMessageParser) {
		return forumMessageParser;
	}

	@Provides
	SessionEncoder provideSessionEncoder(SessionEncoderImpl sessionEncoder) {
		return sessionEncoder;
	}

	@Provides
	SessionParser provideSessionParser(SessionParserImpl sessionParser) {
		return sessionParser;
	}

	@Provides
	ProtocolEngine<Forum> provideForumProtocolEngine(
			ForumProtocolEngineImpl forumProtocolEngine) {
		return forumProtocolEngine;
	}

}
