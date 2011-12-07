package net.sf.briar.transport.stream;

import java.io.IOException;
import java.util.concurrent.Executor;

import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.StreamTransportConnection;

class IncomingStreamConnection extends StreamConnection {

	private final ConnectionContext ctx;
	private final byte[] tag;

	IncomingStreamConnection(@DatabaseExecutor Executor dbExecutor,
			DatabaseComponent db, ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory,
			ProtocolReaderFactory protoReaderFactory,
			ProtocolWriterFactory protoWriterFactory,
			ConnectionContext ctx, StreamTransportConnection connection,
			byte[] tag) {
		super(dbExecutor, db, connReaderFactory, connWriterFactory,
				protoReaderFactory, protoWriterFactory, ctx.getContactId(),
				connection);
		this.ctx = ctx;
		this.tag = tag;
	}

	@Override
	protected ConnectionReader createConnectionReader() throws IOException {
		return connReaderFactory.createConnectionReader(
				connection.getInputStream(), ctx.getSecret(), tag);
	}

	@Override
	protected ConnectionWriter createConnectionWriter() throws IOException {
		return connWriterFactory.createConnectionWriter(
				connection.getOutputStream(), Long.MAX_VALUE, ctx.getSecret(),
				tag);
	}
}
