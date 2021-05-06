package org.briarproject.briar.remotewipe;

import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.sync.validation.ValidationManager;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.remotewipe.RemoteWipeManager;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.briar.api.socialbackup.SocialBackupManager.CLIENT_ID;
import static org.briarproject.briar.api.socialbackup.SocialBackupManager.MAJOR_VERSION;
import static org.briarproject.briar.api.socialbackup.SocialBackupManager.MINOR_VERSION;

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
}
