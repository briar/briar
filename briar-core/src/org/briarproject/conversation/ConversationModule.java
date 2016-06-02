package org.briarproject.conversation;

import org.briarproject.api.conversation.ConversationManager;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ConversationModule {

	public static class EagerSingletons {
		@Inject
		ConversationManager conversationManager;
	}

	@Provides
	@Singleton
	ConversationManager getConversationManager(
			ConversationManagerImpl conversationManager) {
		return conversationManager;
	}
}
