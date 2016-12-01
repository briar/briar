package org.briarproject.briar.messaging;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.sync.ValidationManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.messaging.ConversationManager;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessageFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.briar.messaging.MessagingManagerImpl.CLIENT_ID;

@Module
public class MessagingModule {

	public static class EagerSingletons {
		@Inject
		MessagingManager messagingManager;
		@Inject
		ConversationManager conversationManager;
		@Inject
		PrivateMessageValidator privateMessageValidator;
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
			ContactManager contactManager, ValidationManager validationManager,
			ConversationManager conversationManager,
			MessagingManagerImpl messagingManager) {
		lifecycleManager.registerClient(messagingManager);
		contactManager.registerAddContactHook(messagingManager);
		contactManager.registerRemoveContactHook(messagingManager);
		validationManager
				.registerIncomingMessageHook(CLIENT_ID, messagingManager);
		conversationManager.registerConversationClient(messagingManager);
		return messagingManager;
	}

	@Provides
	@Singleton
	ConversationManager getConversationManager(
			ConversationManagerImpl conversationManager) {
		return conversationManager;
	}

}
