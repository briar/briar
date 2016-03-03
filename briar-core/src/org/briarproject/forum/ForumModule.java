package org.briarproject.forum;

import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ForumModule {

	@Provides
	@Singleton
	ForumManager provideForumManager(DatabaseComponent db,
			ContactManager contactManager,
			BdfReaderFactory bdfReaderFactory, MetadataEncoder metadataEncoder,
			MetadataParser metadataParser) {
		return new ForumManagerImpl(db, contactManager, bdfReaderFactory,
				metadataEncoder, metadataParser);
	}

	@Provides
	ForumPostFactory provideForumPostFactory(CryptoComponent crypto,
			MessageFactory messageFactory,
			BdfWriterFactory bdfWriterFactory) {
		return new ForumPostFactoryImpl(crypto, messageFactory,
				bdfWriterFactory);
	}

	@Provides
	@Singleton
	ForumPostValidator provideForumPostValidator(
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

	@Provides
	@Singleton
	ForumListValidator provideForumListValidator(
			ValidationManager validationManager,
			BdfReaderFactory bdfReaderFactory,
			MetadataEncoder metadataEncoder) {
		ForumListValidator validator = new ForumListValidator(bdfReaderFactory,
				metadataEncoder);
		validationManager.registerMessageValidator(
				ForumSharingManagerImpl.CLIENT_ID, validator);
		return validator;
	}

	@Provides
	@Singleton
	ForumSharingManager provideForumSharingManager(
			ContactManager contactManager,
			ValidationManager validationManager,
			ForumSharingManagerImpl forumSharingManager) {
		contactManager.registerAddContactHook(forumSharingManager);
		contactManager.registerRemoveContactHook(forumSharingManager);
		validationManager.registerIncomingMessageHook(
				ForumSharingManagerImpl.CLIENT_ID, forumSharingManager);
		return forumSharingManager;
	}
}
