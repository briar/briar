package org.briarproject.messaging;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;

import javax.inject.Singleton;

public class MessagingModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(PrivateMessageFactory.class).to(PrivateMessageFactoryImpl.class);
	}

	@Provides @Singleton
	PrivateMessageValidator getValidator(ValidationManager validationManager,
			MessagingManager messagingManager,
			BdfReaderFactory bdfReaderFactory, MetadataEncoder metadataEncoder,
			Clock clock) {
		PrivateMessageValidator validator = new PrivateMessageValidator(
				bdfReaderFactory, metadataEncoder, clock);
		validationManager.registerMessageValidator(
				messagingManager.getClientId(),
				validator);
		return validator;
	}

	@Provides @Singleton
	MessagingManager getMessagingManager(ContactManager contactManager,
			MessagingManagerImpl messagingManager) {
		contactManager.registerAddContactHook(messagingManager);
		contactManager.registerRemoveContactHook(messagingManager);
		return messagingManager;
	}
}
