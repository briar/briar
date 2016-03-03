package org.briarproject.invitation;

import javax.inject.Singleton;

import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.invitation.InvitationTaskFactory;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;

import dagger.Module;
import dagger.Provides;

@Module
public class InvitationModule {

	@Provides
	@Singleton
	InvitationTaskFactory provideInvitationTaskFactory(CryptoComponent crypto,
			BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory,
			StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory,
			AuthorFactory authorFactory, GroupFactory groupFactory,
			KeyManager keyManager, ConnectionManager connectionManager,
			IdentityManager identityManager, ContactManager contactManager,
			Clock clock, PluginManager pluginManager) {
		return new InvitationTaskFactoryImpl(crypto, bdfReaderFactory,
				bdfWriterFactory, streamReaderFactory, streamWriterFactory,
				authorFactory, groupFactory, keyManager, connectionManager,
				identityManager, contactManager, clock, pluginManager);
	}
}
