package org.briarproject.invitation;

import org.briarproject.api.contact.ContactExchangeTask;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.invitation.InvitationTask;
import org.briarproject.api.invitation.InvitationTaskFactory;
import org.briarproject.api.plugins.PluginManager;

import javax.inject.Inject;

class InvitationTaskFactoryImpl implements InvitationTaskFactory {

	private final CryptoComponent crypto;
	private final BdfReaderFactory bdfReaderFactory;
	private final BdfWriterFactory bdfWriterFactory;
	private final ContactExchangeTask contactExchangeTask;
	private final IdentityManager identityManager;
	private final PluginManager pluginManager;

	@Inject
	InvitationTaskFactoryImpl(CryptoComponent crypto,
			BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory,
			ContactExchangeTask contactExchangeTask,
			IdentityManager identityManager, PluginManager pluginManager) {
		this.crypto = crypto;
		this.bdfReaderFactory = bdfReaderFactory;
		this.bdfWriterFactory = bdfWriterFactory;
		this.contactExchangeTask = contactExchangeTask;
		this.identityManager = identityManager;
		this.pluginManager = pluginManager;
	}

	public InvitationTask createTask(int localCode, int remoteCode) {
		return new ConnectorGroup(crypto, bdfReaderFactory, bdfWriterFactory,
				contactExchangeTask, identityManager, pluginManager,
				localCode, remoteCode);
	}
}
