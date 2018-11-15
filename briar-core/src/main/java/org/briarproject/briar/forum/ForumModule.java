package org.briarproject.briar.forum;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.sync.validation.ValidationManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.forum.ForumFactory;
import org.briarproject.briar.api.forum.ForumManager;
import org.briarproject.briar.api.forum.ForumPostFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.briar.api.forum.ForumManager.CLIENT_ID;
import static org.briarproject.briar.api.forum.ForumManager.MAJOR_VERSION;

@Module
public class ForumModule {

	public static class EagerSingletons {
		@Inject
		ForumManager forumManager;
		@Inject
		ForumPostValidator forumPostValidator;
	}

	@Provides
	@Singleton
	ForumManager provideForumManager(ForumManagerImpl forumManager,
			ValidationManager validationManager) {
		validationManager.registerIncomingMessageHook(CLIENT_ID, MAJOR_VERSION,
				forumManager);
		return forumManager;
	}

	@Provides
	ForumPostFactory provideForumPostFactory(
			ForumPostFactoryImpl forumPostFactory) {
		return forumPostFactory;
	}

	@Provides
	ForumFactory provideForumFactory(ForumFactoryImpl forumFactory) {
		return forumFactory;
	}

	@Provides
	@Singleton
	ForumPostValidator provideForumPostValidator(
			ValidationManager validationManager, ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		ForumPostValidator validator = new ForumPostValidator(clientHelper,
				metadataEncoder, clock);
		validationManager.registerMessageValidator(CLIENT_ID, MAJOR_VERSION,
				validator);
		return validator;
	}

}
