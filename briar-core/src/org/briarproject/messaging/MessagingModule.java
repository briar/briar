package org.briarproject.messaging;

import com.google.inject.AbstractModule;

import org.briarproject.api.messaging.MessagingManager;

public class MessagingModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(MessagingManager.class).to(MessagingManagerImpl.class);
	}
}
