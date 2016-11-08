package org.briarproject.introduction;

import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.introduction.IntroductionManager;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.messaging.ConversationManager;
import org.briarproject.api.system.Clock;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.api.introduction.IntroductionManager.CLIENT_ID;
import static org.briarproject.api.sync.ValidationManager.MessageValidator;

@Module
public class IntroductionModule {

	public static class EagerSingletons {
		@Inject IntroductionManager introductionManager;
		@Inject MessageValidator introductionValidator;
	}

	@Provides
	@Singleton
	MessageValidator provideValidator(MessageQueueManager messageQueueManager,
			MetadataEncoder metadataEncoder, ClientHelper clientHelper,
			Clock clock) {

		IntroductionValidator introductionValidator = new IntroductionValidator(
				clientHelper, metadataEncoder, clock);

		messageQueueManager
				.registerMessageValidator(CLIENT_ID, introductionValidator);

		return introductionValidator;
	}

	@Provides
	@Singleton
	IntroductionManager provideIntroductionManager(
			LifecycleManager lifecycleManager,
			ContactManager contactManager,
			MessageQueueManager messageQueueManager,
			ConversationManager conversationManager,
			IntroductionManagerImpl introductionManager) {

		lifecycleManager.registerClient(introductionManager);
		contactManager.registerAddContactHook(introductionManager);
		contactManager.registerRemoveContactHook(introductionManager);
		messageQueueManager
				.registerIncomingMessageHook(CLIENT_ID, introductionManager);
		conversationManager.registerConversationClient(introductionManager);

		return introductionManager;
	}
}
