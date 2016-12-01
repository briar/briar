package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.client.MessageQueueManager;
import org.briarproject.briar.api.introduction.IntroductionManager;
import org.briarproject.briar.api.messaging.ConversationManager;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.briar.api.introduction.IntroductionManager.CLIENT_ID;

@Module
public class IntroductionModule {

	public static class EagerSingletons {
		@Inject
		IntroductionManager introductionManager;
		@Inject
		IntroductionValidator introductionValidator;
	}

	@Provides
	@Singleton
	IntroductionValidator provideValidator(
			MessageQueueManager messageQueueManager,
			MetadataEncoder metadataEncoder, ClientHelper clientHelper,
			Clock clock) {

		IntroductionValidator introductionValidator = new IntroductionValidator(
				clientHelper, metadataEncoder, clock);
		messageQueueManager.registerMessageValidator(CLIENT_ID,
				introductionValidator);

		return introductionValidator;
	}

	@Provides
	@Singleton
	IntroductionManager provideIntroductionManager(
			LifecycleManager lifecycleManager, ContactManager contactManager,
			MessageQueueManager messageQueueManager,
			ConversationManager conversationManager,
			IntroductionManagerImpl introductionManager) {

		lifecycleManager.registerClient(introductionManager);
		contactManager.registerAddContactHook(introductionManager);
		contactManager.registerRemoveContactHook(introductionManager);
		messageQueueManager.registerIncomingMessageHook(CLIENT_ID,
				introductionManager);
		conversationManager.registerConversationClient(introductionManager);

		return introductionManager;
	}
}
