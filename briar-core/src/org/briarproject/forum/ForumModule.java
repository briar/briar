package org.briarproject.forum;

import org.briarproject.api.clients.ClientHelper;
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
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.data.DataModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.sync.SyncModule;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ForumModule {

	@Provides
	@Singleton
	ForumManager provideForumManager(DatabaseComponent db,
			ContactManager contactManager, BdfReaderFactory bdfReaderFactory,
			MetadataEncoder metadataEncoder, MetadataParser metadataParser) {
		return new ForumManagerImpl(db, bdfReaderFactory, metadataEncoder,
				metadataParser);
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
	ForumListValidator provideForumListValidator(
			ValidationManager validationManager, ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		ForumListValidator validator = new ForumListValidator(clientHelper,
				metadataEncoder, clock);
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
