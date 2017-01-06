package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.sync.ValidationManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogFactory;
import org.briarproject.briar.api.blog.BlogInvitationResponse;
import org.briarproject.briar.api.blog.BlogManager;
import org.briarproject.briar.api.blog.BlogSharingManager;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumFactory;
import org.briarproject.briar.api.forum.ForumInvitationResponse;
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
	MessageEncoder provideMessageEncoder(MessageEncoderImpl messageEncoder) {
		return messageEncoder;
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
	@Singleton
	BlogSharingValidator provideBlogSharingValidator(
			ValidationManager validationManager, MessageEncoder messageEncoder,
			ClientHelper clientHelper, MetadataEncoder metadataEncoder,
			Clock clock, BlogFactory blogFactory, AuthorFactory authorFactory) {
		BlogSharingValidator validator =
				new BlogSharingValidator(messageEncoder, clientHelper,
						metadataEncoder, clock, blogFactory, authorFactory);
		validationManager.registerMessageValidator(BlogSharingManager.CLIENT_ID,
				validator);
		return validator;
	}

	@Provides
	@Singleton
	BlogSharingManager provideBlogSharingManager(
			LifecycleManager lifecycleManager, ContactManager contactManager,
			ValidationManager validationManager,
			ConversationManager conversationManager, BlogManager blogManager,
			BlogSharingManagerImpl blogSharingManager) {
		lifecycleManager.registerClient(blogSharingManager);
		contactManager.registerAddContactHook(blogSharingManager);
		contactManager.registerRemoveContactHook(blogSharingManager);
		validationManager.registerIncomingMessageHook(
				BlogSharingManager.CLIENT_ID, blogSharingManager);
		conversationManager.registerConversationClient(blogSharingManager);
		blogManager.registerRemoveBlogHook(blogSharingManager);

		return blogSharingManager;
	}

	@Provides
	MessageParser<Blog> provideBlogMessageParser(
			BlogMessageParserImpl blogMessageParser) {
		return blogMessageParser;
	}

	@Provides
	ProtocolEngine<Blog> provideBlogProtocolEngine(
			BlogProtocolEngineImpl blogProtocolEngine) {
		return blogProtocolEngine;
	}

	@Provides
	InvitationFactory<Blog, BlogInvitationResponse> provideBlogInvitationFactory(
			BlogInvitationFactoryImpl blogInvitationFactory) {
		return blogInvitationFactory;
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
	MessageParser<Forum> provideForumMessageParser(
			ForumMessageParserImpl forumMessageParser) {
		return forumMessageParser;
	}

	@Provides
	ProtocolEngine<Forum> provideForumProtocolEngine(
			ForumProtocolEngineImpl forumProtocolEngine) {
		return forumProtocolEngine;
	}

	@Provides
	InvitationFactory<Forum, ForumInvitationResponse> provideForumInvitationFactory(
			ForumInvitationFactoryImpl forumInvitationFactory) {
		return forumInvitationFactory;
	}

}
