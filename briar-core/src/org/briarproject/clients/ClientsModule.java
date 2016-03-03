package org.briarproject.clients;

import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.PrivateGroupFactory;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.MessageFactory;

import dagger.Module;
import dagger.Provides;

@Module
public class ClientsModule {

	@Provides
	ClientHelper provideClientHelper(DatabaseComponent db,
			MessageFactory messageFactory, BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory, MetadataParser metadataParser,
			MetadataEncoder metadataEncoder) {
		return new ClientHelperImpl(db, messageFactory, bdfReaderFactory,
				bdfWriterFactory, metadataParser, metadataEncoder);
	}

	@Provides
	PrivateGroupFactory providePrivateGroupFactory(GroupFactory groupFactory,
			BdfWriterFactory bdfWriterFactory) {
		return new PrivateGroupFactoryImpl(groupFactory, bdfWriterFactory);
	}

		bind(QueueMessageFactory.class).to(QueueMessageFactoryImpl.class);

}
