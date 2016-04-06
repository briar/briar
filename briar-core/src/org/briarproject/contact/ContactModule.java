package org.briarproject.contact;

import org.briarproject.api.contact.ContactExchangeTask;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ContactModule {

	public static class EagerSingletons {
		@Inject ContactManager contactManager;
	}

	@Provides
	@Singleton
	ContactManager getContactManager(IdentityManager identityManager,
			ContactManagerImpl contactManager) {
		identityManager.registerRemoveIdentityHook(contactManager);
		return contactManager;
	}

	@Provides
	ContactExchangeTask provideContactExchangeTask(DatabaseComponent db,
			AuthorFactory authorFactory, BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory, Clock clock,
			ConnectionManager connectionManager, ContactManager contactManager,
			TransportPropertyManager transportPropertyManager,
			CryptoComponent crypto, StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory) {
		return new ContactExchangeTaskImpl(db, authorFactory, bdfReaderFactory,
				bdfWriterFactory, clock, connectionManager, contactManager,
				transportPropertyManager, crypto, streamReaderFactory,
				streamWriterFactory);
	}
}
