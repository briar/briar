package org.briarproject.messaging;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.messaging.PrivateMessageFactory;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;

import javax.inject.Singleton;

public class MessagingModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(MessagingManager.class).to(MessagingManagerImpl.class);
		bind(PrivateMessageFactory.class).to(PrivateMessageFactoryImpl.class);
	}

	@Provides @Singleton
	PrivateMessageValidator getValidator(LifecycleManager lifecycleManager,
			ValidationManager validationManager,
			BdfReaderFactory bdfReaderFactory, MetadataEncoder metadataEncoder,
			Clock clock) {
		PrivateMessageValidator validator = new PrivateMessageValidator(
				validationManager, bdfReaderFactory, metadataEncoder, clock);
		lifecycleManager.register(validator);
		return validator;
	}
}
