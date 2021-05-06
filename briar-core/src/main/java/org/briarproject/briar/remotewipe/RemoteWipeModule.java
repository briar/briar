package org.briarproject.briar.remotewipe;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.sync.validation.ValidationManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.remotewipe.MessageEncoder;
import org.briarproject.briar.api.remotewipe.MessageParser;
import org.briarproject.briar.api.remotewipe.RemoteWipeManager;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.briar.api.remotewipe.RemoteWipeManager.CLIENT_ID;
import static org.briarproject.briar.api.remotewipe.RemoteWipeManager.MAJOR_VERSION;
import static org.briarproject.briar.api.remotewipe.RemoteWipeManager.MINOR_VERSION;

@Module
public class RemoteWipeModule {
	public static class EagerSingletons {
		@Inject
		RemoteWipeManager remoteWipeManager;
		@Inject
		RemoteWipeValidator remoteWipeValidator;
	}

	@Provides
	@Singleton
	RemoteWipeManager remoteWipeManager(
			LifecycleManager lifecycleManager,
			ValidationManager validationManager,
			ConversationManager conversationManager,
			ContactManager contactManager,
			ClientVersioningManager clientVersioningManager,
			RemoteWipeManagerImpl remoteWipeManager) {
		lifecycleManager.registerOpenDatabaseHook(remoteWipeManager);
		validationManager
				.registerIncomingMessageHook(RemoteWipeManager.CLIENT_ID,
						RemoteWipeManager.MAJOR_VERSION, remoteWipeManager);

		contactManager.registerContactHook(remoteWipeManager);
		clientVersioningManager.registerClient(CLIENT_ID, MAJOR_VERSION,
				MINOR_VERSION, remoteWipeManager);
		conversationManager.registerConversationClient(remoteWipeManager);
		return remoteWipeManager;
	}

	@Provides
	@Singleton
	RemoteWipeValidator remoteWipeValidator(
			ValidationManager validationManager,
			ClientHelper clientHelper,
			MetadataEncoder metadataEncoder,
			Clock clock) {
		RemoteWipeValidator validator =
				new RemoteWipeValidator(clientHelper, metadataEncoder, clock);
		validationManager.registerMessageValidator(CLIENT_ID, MAJOR_VERSION,
				validator);
		return validator;
	}

	@Provides
	MessageEncoder messageEncoder(MessageEncoderImpl messageEncoder) {
		return messageEncoder;
	}

	@Provides
	MessageParser messageParser(MessageParserImpl messageParser) {
		return messageParser;
	}
}
