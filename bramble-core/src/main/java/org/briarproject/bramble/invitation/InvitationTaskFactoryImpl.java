package org.briarproject.bramble.invitation;

import org.briarproject.bramble.api.contact.ContactExchangeTask;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.data.BdfReaderFactory;
import org.briarproject.bramble.api.data.BdfWriterFactory;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.invitation.InvitationTask;
import org.briarproject.bramble.api.invitation.InvitationTaskFactory;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginManager;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
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

	@Override
	public InvitationTask createTask(int localCode, int remoteCode) {
		return new ConnectorGroup(crypto, bdfReaderFactory, bdfWriterFactory,
				contactExchangeTask, identityManager, pluginManager,
				localCode, remoteCode);
	}
}
