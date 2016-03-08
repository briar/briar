package org.briarproject.messaging;

import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;
import org.briarproject.contact.ContactModule;
import org.briarproject.data.DataModule;
import org.briarproject.sync.SyncModule;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.messaging.MessagingManagerImpl.CLIENT_ID;

@Module
public class MessagingModule {

	@Provides
	PrivateMessageFactory providePrivateMessageFactory(
			MessageFactory messageFactory,
			BdfWriterFactory bdfWriterFactory) {
		return new PrivateMessageFactoryImpl(messageFactory, bdfWriterFactory);
	}


	@Provides
	@Singleton
	PrivateMessageValidator getValidator(ValidationManager validationManager,
			BdfReaderFactory bdfReaderFactory, MetadataEncoder metadataEncoder,
			Clock clock) {
		PrivateMessageValidator validator = new PrivateMessageValidator(
				bdfReaderFactory, metadataEncoder, clock);
		validationManager.registerMessageValidator(CLIENT_ID, validator);
		return validator;
	}

	@Provides
	@Singleton
	MessagingManager getMessagingManager(ContactManager contactManager,
			MessagingManagerImpl messagingManager) {
		contactManager.registerAddContactHook(messagingManager);
		contactManager.registerRemoveContactHook(messagingManager);
		return messagingManager;
	}
}
