package net.sf.briar.invitation;

import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.invitation.InvitationTask;
import net.sf.briar.api.invitation.InvitationTaskFactory;
import net.sf.briar.api.plugins.PluginManager;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.WriterFactory;

import com.google.inject.Inject;

class InvitationTaskFactoryImpl implements InvitationTaskFactory {

	private final CryptoComponent crypto;
	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final Clock clock;
	private final PluginManager pluginManager;

	@Inject
	InvitationTaskFactoryImpl(CryptoComponent crypto,
			ReaderFactory readerFactory, WriterFactory writerFactory,
			Clock clock, PluginManager pluginManager) {
		this.crypto = crypto;
		this.readerFactory = readerFactory;
		this.writerFactory = writerFactory;
		this.clock = clock;
		this.pluginManager = pluginManager;
	}

	public InvitationTask createTask(int localCode, int remoteCode) {
		return new ConnectorGroup(crypto, readerFactory, writerFactory, clock,
				pluginManager, localCode, remoteCode);
	}
}
