package org.briarproject.invitation;

import javax.inject.Inject;

import org.briarproject.api.AuthorFactory;
import org.briarproject.api.AuthorId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyManager;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.invitation.InvitationTask;
import org.briarproject.api.invitation.InvitationTaskFactory;
import org.briarproject.api.messaging.GroupFactory;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.serial.ReaderFactory;
import org.briarproject.api.serial.WriterFactory;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.ConnectionDispatcher;
import org.briarproject.api.transport.ConnectionReaderFactory;
import org.briarproject.api.transport.ConnectionWriterFactory;

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
