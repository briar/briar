package org.briarproject.forum;

import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.forum.ForumFactory;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.identity.AuthorFactory;
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
	}

	@Provides
	@Singleton
	ForumManager provideForumManager(ForumManagerImpl forumManager,
			ValidationManager validationManager) {

		validationManager
				.registerIncomingMessageHook(forumManager.getClientId(),
						forumManager);

		return forumManager;
	}

	@Provides
	ForumPostFactory provideForumPostFactory(CryptoComponent crypto,
			ClientHelper clientHelper) {
		return new ForumPostFactoryImpl(crypto, clientHelper);
	}

	@Provides
	ForumFactory provideForumFactory(GroupFactory groupFactory,
			ClientHelper clientHelper, SecureRandom random) {
		return new ForumFactoryImpl(groupFactory, clientHelper, random);
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

}
