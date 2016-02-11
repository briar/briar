package org.briarproject.forum;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;

import javax.inject.Singleton;

public class ForumModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ForumManager.class).to(ForumManagerImpl.class).in(Singleton.class);
		bind(ForumPostFactory.class).to(ForumPostFactoryImpl.class);
	}

	@Provides @Singleton
	ForumPostValidator getForumPostValidator(
			ValidationManager validationManager, CryptoComponent crypto,
			BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory,
			ObjectReader<Author> authorReader, MetadataEncoder metadataEncoder,
			Clock clock) {
		ForumPostValidator validator = new ForumPostValidator(crypto,
				bdfReaderFactory, bdfWriterFactory, authorReader,
				metadataEncoder, clock);
		validationManager.registerMessageValidator(
				ForumManagerImpl.CLIENT_ID, validator);
		return validator;
	}

	@Provides @Singleton
	ForumListValidator getForumListValidator(
			ValidationManager validationManager,
			BdfReaderFactory bdfReaderFactory,
			MetadataEncoder metadataEncoder) {
		ForumListValidator validator = new ForumListValidator(bdfReaderFactory,
				metadataEncoder);
		validationManager.registerMessageValidator(
				ForumSharingManagerImpl.CLIENT_ID, validator);
		return validator;
	}

	@Provides @Singleton
	ForumSharingManager getForumSharingManager(ContactManager contactManager,
			ValidationManager validationManager,
			ForumSharingManagerImpl forumSharingManager) {
		contactManager.registerAddContactHook(forumSharingManager);
		contactManager.registerRemoveContactHook(forumSharingManager);
		validationManager.registerValidationHook(forumSharingManager);
		return forumSharingManager;
	}
}
