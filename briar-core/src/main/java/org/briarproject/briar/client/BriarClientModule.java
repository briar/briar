package org.briarproject.briar.client;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.sync.ValidationManager;
import org.briarproject.briar.api.client.MessageQueueManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.QueueMessageFactory;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class BriarClientModule {

	@Provides
	@Singleton
	MessageQueueManager provideMessageQueueManager(DatabaseComponent db,
			ClientHelper clientHelper, QueueMessageFactory queueMessageFactory,
			ValidationManager validationManager) {
		return new MessageQueueManagerImpl(db, clientHelper,
				queueMessageFactory, validationManager);
	}

	@Provides
	QueueMessageFactory provideQueueMessageFactory(CryptoComponent crypto) {
		return new QueueMessageFactoryImpl(crypto);
	}

	@Provides
	MessageTracker provideMessageTracker(MessageTrackerImpl messageTracker) {
		return messageTracker;
	}
}
