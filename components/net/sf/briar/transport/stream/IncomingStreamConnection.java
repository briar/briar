package net.sf.briar.transport.stream;

import java.io.IOException;

import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.writers.ProtocolWriterFactory;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.StreamTransportConnection;

class IncomingStreamConnection extends StreamConnection {

	private final ConnectionContext ctx;
	private final byte[] encryptedIv;

	IncomingStreamConnection(ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory, DatabaseComponent db,
			ProtocolReaderFactory protoReaderFactory,
			ProtocolWriterFactory protoWriterFactory,
			ConnectionContext ctx, StreamTransportConnection connection,
			byte[] encryptedIv) {
		super(connReaderFactory, connWriterFactory, db, protoReaderFactory,
				protoWriterFactory, ctx.getContactId(), connection);
		this.ctx = ctx;
		this.encryptedIv = encryptedIv;
	}

	@Override
	protected ConnectionReader createConnectionReader() throws DbException,
	IOException {
		return connReaderFactory.createConnectionReader(
				connection.getInputStream(), ctx, encryptedIv);
	}

	@Override
	protected ConnectionWriter createConnectionWriter() throws DbException,
	IOException {
		return connWriterFactory.createConnectionWriter(
				connection.getOutputStream(), Long.MAX_VALUE, ctx, encryptedIv);
	}
}
