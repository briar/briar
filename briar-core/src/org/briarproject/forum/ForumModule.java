package org.briarproject.forum;

import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;

import java.security.SecureRandom;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ForumModule {

	public static class EagerSingletons {
		@Inject
		ForumPostValidator forumPostValidator;
		@Inject
		ForumSharingValidator forumSharingValidator;
		@Inject
		ForumSharingManager forumSharingManager;
	}

	@Provides
	@Singleton
	ForumManager provideForumManager(DatabaseComponent db,
			ClientHelper clientHelper,
			GroupFactory groupFactory, SecureRandom random) {
		return new ForumManagerImpl(db, clientHelper, groupFactory, random);
	}

	@Provides
	ForumPostFactory provideForumPostFactory(CryptoComponent crypto,
			ClientHelper clientHelper) {
		return new ForumPostFactoryImpl(crypto, clientHelper);
	}

	@Provides
	@Singleton
	ForumPostValidator provideForumPostValidator(
			ValidationManager validationManager, CryptoComponent crypto,
			AuthorFactory authorFactory, ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		ForumPostValidator validator = new ForumPostValidator(crypto,
				authorFactory, clientHelper, metadataEncoder, clock);
		validationManager.registerMessageValidator(
				ForumManagerImpl.CLIENT_ID, validator);
		return validator;
	}

	@Provides
	@Singleton
	ForumSharingValidator provideSharingValidator(
			MessageQueueManager messageQueueManager, ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {

		ForumSharingValidator validator = new ForumSharingValidator(clientHelper,
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
			ForumManager forumManager,
			ForumSharingManagerImpl forumSharingManager) {

		lifecycleManager.registerClient(forumSharingManager);
		contactManager.registerAddContactHook(forumSharingManager);
		contactManager.registerRemoveContactHook(forumSharingManager);
		messageQueueManager.registerIncomingMessageHook(
				ForumSharingManagerImpl.CLIENT_ID, forumSharingManager);
		forumManager.registerRemoveForumHook(forumSharingManager);

		return forumSharingManager;
	}
}
