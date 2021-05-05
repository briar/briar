package org.briarproject.briar.remotewipe;

import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.sync.validation.ValidationManager;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.remotewipe.RemoteWipeManager;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class RemoteWipeModule {
	public static class EagerSingletons {
		@Inject
		RemoteWipeManager remoteWipeManager;
	}

	@Provides
	@Singleton
	RemoteWipeManager remoteWipeManager(
			LifecycleManager lifecycleManager,
			ValidationManager validationManager,
			ConversationManager conversationManager,
			RemoteWipeManagerImpl remoteWipeManager) {
		lifecycleManager.registerOpenDatabaseHook(remoteWipeManager);
		validationManager
				.registerIncomingMessageHook(RemoteWipeManager.CLIENT_ID,
						RemoteWipeManager.MAJOR_VERSION, remoteWipeManager);
		conversationManager.registerConversationClient(remoteWipeManager);
		return remoteWipeManager;
	}
}
