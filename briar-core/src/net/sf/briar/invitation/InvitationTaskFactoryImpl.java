package net.sf.briar.invitation;

import javax.inject.Inject;

import net.sf.briar.api.AuthorFactory;
import net.sf.briar.api.AuthorId;
import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.invitation.InvitationTask;
import net.sf.briar.api.invitation.InvitationTaskFactory;
import net.sf.briar.api.messaging.GroupFactory;
import net.sf.briar.api.plugins.PluginManager;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriterFactory;

class InvitationTaskFactoryImpl implements InvitationTaskFactory {

	private final CryptoComponent crypto;
	private final DatabaseComponent db;
	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final ConnectionReaderFactory connectionReaderFactory;
	private final ConnectionWriterFactory connectionWriterFactory;
	private final AuthorFactory authorFactory;
	private final GroupFactory groupFactory;
	private final KeyManager keyManager;
	private final ConnectionDispatcher connectionDispatcher;
	private final Clock clock;
	private final PluginManager pluginManager;

	@Inject
	InvitationTaskFactoryImpl(CryptoComponent crypto, DatabaseComponent db,
			ReaderFactory readerFactory, WriterFactory writerFactory,
			ConnectionReaderFactory connectionReaderFactory,
			ConnectionWriterFactory connectionWriterFactory,
			AuthorFactory authorFactory, GroupFactory groupFactory,
			KeyManager keyManager, ConnectionDispatcher connectionDispatcher,
			Clock clock, PluginManager pluginManager) {
		this.crypto = crypto;
		this.db = db;
		this.readerFactory = readerFactory;
		this.writerFactory = writerFactory;
		this.connectionReaderFactory = connectionReaderFactory;
		this.connectionWriterFactory = connectionWriterFactory;
		this.authorFactory = authorFactory;
		this.groupFactory = groupFactory;
		this.keyManager = keyManager;
		this.connectionDispatcher = connectionDispatcher;
		this.clock = clock;
		this.pluginManager = pluginManager;
	}

	public InvitationTask createTask(AuthorId localAuthorId, int localCode,
			int remoteCode) {
		return new ConnectorGroup(crypto, db, readerFactory, writerFactory,
				connectionReaderFactory, connectionWriterFactory,
				authorFactory, groupFactory, keyManager, connectionDispatcher,
				clock, pluginManager, localAuthorId, localCode, remoteCode);
	}
}
