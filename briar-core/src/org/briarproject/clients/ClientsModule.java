package org.briarproject.clients;

import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.clients.PrivateGroupFactory;
import org.briarproject.api.clients.QueueMessageFactory;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.ValidationManager;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ClientsModule {

	@Provides
	ClientHelper provideClientHelper(DatabaseComponent db,
			MessageFactory messageFactory, BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory, MetadataParser metadataParser,
			MetadataEncoder metadataEncoder, CryptoComponent cryptoComponent) {
		return new ClientHelperImpl(db, messageFactory, bdfReaderFactory,
				bdfWriterFactory, metadataParser, metadataEncoder,
				cryptoComponent);
	}

	@Provides
	PrivateGroupFactory providePrivateGroupFactory(GroupFactory groupFactory,
			ClientHelper clientHelper) {
		return new PrivateGroupFactoryImpl(groupFactory, clientHelper);
	}

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

}
