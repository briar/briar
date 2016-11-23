package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.sync.GroupFactory;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.PacketReaderFactory;
import org.briarproject.bramble.api.sync.PacketWriterFactory;
import org.briarproject.bramble.api.sync.SyncSessionFactory;
import org.briarproject.bramble.api.sync.ValidationManager;
import org.briarproject.bramble.api.system.Clock;

import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class SyncModule {

	public static class EagerSingletons {
		@Inject
		ValidationManager validationManager;
	}

	@Provides
	GroupFactory provideGroupFactory(CryptoComponent crypto) {
		return new GroupFactoryImpl(crypto);
	}

	@Provides
	MessageFactory provideMessageFactory(CryptoComponent crypto) {
		return new MessageFactoryImpl(crypto);
	}

	@Provides
	PacketReaderFactory providePacketReaderFactory(CryptoComponent crypto) {
		return new PacketReaderFactoryImpl(crypto);
	}

	@Provides
	PacketWriterFactory providePacketWriterFactory() {
		return new PacketWriterFactoryImpl();
	}

	@Provides
	@Singleton
	SyncSessionFactory provideSyncSessionFactory(DatabaseComponent db,
			@DatabaseExecutor Executor dbExecutor, EventBus eventBus,
			Clock clock, PacketReaderFactory packetReaderFactory,
			PacketWriterFactory packetWriterFactory) {
		return new SyncSessionFactoryImpl(db, dbExecutor, eventBus, clock,
				packetReaderFactory, packetWriterFactory);
	}

	@Provides
	@Singleton
	ValidationManager getValidationManager(LifecycleManager lifecycleManager,
			EventBus eventBus, ValidationManagerImpl validationManager) {
		lifecycleManager.registerService(validationManager);
		eventBus.addListener(validationManager);
		return validationManager;
	}
}
