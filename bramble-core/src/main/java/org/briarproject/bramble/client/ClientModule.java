package org.briarproject.bramble.client;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.data.BdfReaderFactory;
import org.briarproject.bramble.api.data.BdfWriterFactory;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.sync.GroupFactory;
import org.briarproject.bramble.api.sync.MessageFactory;

import dagger.Module;
import dagger.Provides;

@Module
public class ClientModule {

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
	ContactGroupFactory provideContactGroupFactory(GroupFactory groupFactory,
			ClientHelper clientHelper) {
		return new ContactGroupFactoryImpl(groupFactory, clientHelper);
	}

}
