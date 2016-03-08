package org.briarproject.sync;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.data.ObjectReader;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.PacketReaderFactory;
import org.briarproject.api.sync.PacketWriterFactory;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.api.sync.ValidationManager;
import org.briarproject.api.system.Clock;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.event.EventModule;
import org.briarproject.lifecycle.LifecycleModule;

import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class SyncModule {

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
		lifecycleManager.register(validationManager);
		eventBus.addListener(validationManager);
		return validationManager;
	}
}
