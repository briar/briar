package org.briarproject.sync;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import org.briarproject.api.event.EventBus;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.PacketReaderFactory;
import org.briarproject.api.sync.PacketWriterFactory;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.api.sync.ValidationManager;

import javax.inject.Singleton;

public class SyncModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(GroupFactory.class).to(GroupFactoryImpl.class);
		bind(MessageFactory.class).to(MessageFactoryImpl.class);
		bind(PacketReaderFactory.class).to(PacketReaderFactoryImpl.class);
		bind(PacketWriterFactory.class).to(PacketWriterFactoryImpl.class);
		bind(SyncSessionFactory.class).to(
				SyncSessionFactoryImpl.class).in(Singleton.class);
	}

	@Provides @Singleton
	ValidationManager getValidationManager(LifecycleManager lifecycleManager,
			EventBus eventBus, ValidationManagerImpl validationManager) {
		lifecycleManager.register(validationManager);
		eventBus.addListener(validationManager);
		return validationManager;
	}
}
