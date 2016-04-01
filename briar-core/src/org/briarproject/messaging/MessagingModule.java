package org.briarproject.messaging;

import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.messaging.MessagingManagerImpl.CLIENT_ID;

@Module
public class MessagingModule {

	public static class EagerSingletons {
		@Inject MessagingManager messagingManager;
		@Inject PrivateMessageValidator privateMessageValidator;
	}

	@Provides
	PrivateMessageFactory providePrivateMessageFactory(
			ClientHelper clientHelper) {
		return new PrivateMessageFactoryImpl(clientHelper);
	}

	@Provides
	@Singleton
	PrivateMessageValidator getValidator(ValidationManager validationManager,
			ClientHelper clientHelper, MetadataEncoder metadataEncoder,
			Clock clock) {
		PrivateMessageValidator validator = new PrivateMessageValidator(
				clientHelper, metadataEncoder, clock);
		validationManager.registerMessageValidator(CLIENT_ID, validator);
		return validator;
	}

	@Provides
	@Singleton
	MessagingManager getMessagingManager(LifecycleManager lifecycleManager,
			ContactManager contactManager,
			MessagingManagerImpl messagingManager) {
		lifecycleManager.registerClient(messagingManager);
		contactManager.registerAddContactHook(messagingManager);
		contactManager.registerRemoveContactHook(messagingManager);
		return messagingManager;
	}
}
