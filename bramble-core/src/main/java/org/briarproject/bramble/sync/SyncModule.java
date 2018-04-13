package org.briarproject.bramble.sync;

import org.briarproject.bramble.PoliteExecutor;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.sync.ClientVersioningManager;
import org.briarproject.bramble.api.sync.GroupFactory;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.RecordReaderFactory;
import org.briarproject.bramble.api.sync.RecordWriterFactory;
import org.briarproject.bramble.api.sync.SyncSessionFactory;
import org.briarproject.bramble.api.sync.ValidationManager;
import org.briarproject.bramble.api.system.Clock;

import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.bramble.api.sync.ClientVersioningManager.CLIENT_ID;
import static org.briarproject.bramble.api.sync.ClientVersioningManager.CLIENT_VERSION;

@Module
public class SyncModule {

	public static class EagerSingletons {
		@Inject
		ValidationManager validationManager;
		@Inject
		ClientVersioningManager clientVersioningManager;
		@Inject
		ClientVersioningValidator clientVersioningValidator;
	}

	/**
	 * The maximum number of validation tasks to delegate to the crypto
	 * executor concurrently.
	 * <p>
	 * The number of available processors can change during the lifetime of the
	 * JVM, so this is just a reasonable guess.
	 */
	private static final int MAX_CONCURRENT_VALIDATION_TASKS =
			Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

	@Provides
	GroupFactory provideGroupFactory(CryptoComponent crypto) {
		return new GroupFactoryImpl(crypto);
	}

	@Provides
	MessageFactory provideMessageFactory(CryptoComponent crypto) {
		return new MessageFactoryImpl(crypto);
	}

	@Provides
	RecordReaderFactory provideRecordReaderFactory(
			RecordReaderFactoryImpl recordReaderFactory) {
		return recordReaderFactory;
	}

	@Provides
	RecordWriterFactory provideRecordWriterFactory() {
		return new RecordWriterFactoryImpl();
	}

	@Provides
	@Singleton
	SyncSessionFactory provideSyncSessionFactory(DatabaseComponent db,
			@DatabaseExecutor Executor dbExecutor, EventBus eventBus,
			Clock clock, RecordReaderFactory recordReaderFactory,
			RecordWriterFactory recordWriterFactory) {
		return new SyncSessionFactoryImpl(db, dbExecutor, eventBus, clock,
				recordReaderFactory, recordWriterFactory);
	}

	@Provides
	@Singleton
	ValidationManager provideValidationManager(
			LifecycleManager lifecycleManager, EventBus eventBus,
			ValidationManagerImpl validationManager) {
		lifecycleManager.registerService(validationManager);
		eventBus.addListener(validationManager);
		return validationManager;
	}

	@Provides
	@Singleton
	@ValidationExecutor
	Executor provideValidationExecutor(
			@CryptoExecutor Executor cryptoExecutor) {
		return new PoliteExecutor("ValidationExecutor", cryptoExecutor,
				MAX_CONCURRENT_VALIDATION_TASKS);
	}

	@Provides
	@Singleton
	ClientVersioningManager provideClientVersioningManager(
			ClientVersioningManagerImpl clientVersioningManager,
			LifecycleManager lifecycleManager, ContactManager contactManager,
			ValidationManager validationManager) {
		lifecycleManager.registerClient(clientVersioningManager);
		lifecycleManager.registerService(clientVersioningManager);
		contactManager.registerContactHook(clientVersioningManager);
		validationManager.registerIncomingMessageHook(CLIENT_ID, CLIENT_VERSION,
				clientVersioningManager);
		return clientVersioningManager;
	}

	@Provides
	@Singleton
	ClientVersioningValidator provideClientVersioningValidator(
			ClientHelper clientHelper, MetadataEncoder metadataEncoder,
			Clock clock, ValidationManager validationManager) {
		ClientVersioningValidator validator = new ClientVersioningValidator(
				clientHelper, metadataEncoder, clock);
		validationManager.registerMessageValidator(CLIENT_ID, CLIENT_VERSION,
				validator);
		return validator;
	}
}
