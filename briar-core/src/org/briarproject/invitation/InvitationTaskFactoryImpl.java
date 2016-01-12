package org.briarproject.invitation;

import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.invitation.InvitationTask;
import org.briarproject.api.invitation.InvitationTaskFactory;
import org.briarproject.api.messaging.MessagingManager;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.property.TransportPropertyManager;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.KeyManager;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriterFactory;

import javax.inject.Inject;

class InvitationTaskFactoryImpl implements InvitationTaskFactory {

	private final CryptoComponent crypto;
	private final BdfReaderFactory bdfReaderFactory;
	private final BdfWriterFactory bdfWriterFactory;
	private final StreamReaderFactory streamReaderFactory;
	private final StreamWriterFactory streamWriterFactory;
	private final AuthorFactory authorFactory;
	private final GroupFactory groupFactory;
	private final KeyManager keyManager;
	private final ConnectionManager connectionManager;
	private final IdentityManager identityManager;
	private final ContactManager contactManager;
	private final MessagingManager messagingManager;
	private final TransportPropertyManager transportPropertyManager;
	private final Clock clock;
	private final PluginManager pluginManager;

	@Inject
	InvitationTaskFactoryImpl(CryptoComponent crypto,
			BdfReaderFactory bdfReaderFactory, BdfWriterFactory bdfWriterFactory,
			StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory,
			AuthorFactory authorFactory, GroupFactory groupFactory,
			KeyManager keyManager, ConnectionManager connectionManager,
			IdentityManager identityManager, ContactManager contactManager,
			MessagingManager messagingManager,
			TransportPropertyManager transportPropertyManager,
			Clock clock, PluginManager pluginManager) {
		this.crypto = crypto;
		this.bdfReaderFactory = bdfReaderFactory;
		this.bdfWriterFactory = bdfWriterFactory;
		this.streamReaderFactory = streamReaderFactory;
		this.streamWriterFactory = streamWriterFactory;
		this.authorFactory = authorFactory;
		this.groupFactory = groupFactory;
		this.keyManager = keyManager;
		this.connectionManager = connectionManager;
		this.identityManager = identityManager;
		this.contactManager = contactManager;
		this.messagingManager = messagingManager;
		this.transportPropertyManager = transportPropertyManager;
		this.clock = clock;
		this.pluginManager = pluginManager;
	}

	public InvitationTask createTask(AuthorId localAuthorId, int localCode,
			int remoteCode, boolean reuseConnection) {
		return new ConnectorGroup(crypto, bdfReaderFactory, bdfWriterFactory,
				streamReaderFactory, streamWriterFactory, authorFactory,
				groupFactory, keyManager, connectionManager, identityManager,
				contactManager, messagingManager, transportPropertyManager,
				clock, pluginManager, localAuthorId, localCode, remoteCode,
				reuseConnection);
	}
}
