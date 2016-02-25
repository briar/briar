package org.briarproject.contact;

import org.briarproject.api.contact.ContactExchangeTask;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.KeyManager;
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
	ContactManager getContactManager(LifecycleManager lifecycleManager,
			IdentityManager identityManager,
			ContactManagerImpl contactManager) {
		identityManager.registerRemoveIdentityHook(contactManager);
		return contactManager;
	}

	@Provides
	ContactExchangeTask provideContactExchangeTask(
			AuthorFactory authorFactory, BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory, Clock clock,
			ConnectionManager connectionManager, ContactManager contactManager,
			CryptoComponent crypto, KeyManager keyManager,
			StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory) {
		return new ContactExchangeTaskImpl(authorFactory,
				bdfReaderFactory, bdfWriterFactory, clock, connectionManager,
				contactManager, crypto, keyManager, streamReaderFactory,
				streamWriterFactory);
	}
}
