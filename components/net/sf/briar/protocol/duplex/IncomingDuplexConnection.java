package net.sf.briar.protocol.duplex;

import java.io.IOException;
import java.util.concurrent.Executor;

import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.VerificationExecutor;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;

class IncomingDuplexConnection extends DuplexConnection {

	private final ConnectionContext ctx;
	private final byte[] tag;

	IncomingDuplexConnection(@DatabaseExecutor Executor dbExecutor,
			@VerificationExecutor Executor verificationExecutor,
			DatabaseComponent db, ConnectionRegistry connRegistry,
			ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory,
			ProtocolReaderFactory protoReaderFactory,
			ProtocolWriterFactory protoWriterFactory,
			ConnectionContext ctx, TransportId transportId,
			DuplexTransportConnection transport, byte[] tag) {
		super(dbExecutor, verificationExecutor, db, connRegistry,
				connReaderFactory, connWriterFactory, protoReaderFactory,
				protoWriterFactory, ctx.getContactId(), transportId, transport);
		this.ctx = ctx;
		this.tag = tag;
	}

	@Override
	protected ConnectionReader createConnectionReader() throws IOException {
		return connReaderFactory.createConnectionReader(
				transport.getInputStream(), ctx.getSecret(), tag);
	}

	@Override
	protected ConnectionWriter createConnectionWriter() throws IOException {
		return connWriterFactory.createConnectionWriter(
				transport.getOutputStream(), Long.MAX_VALUE, ctx.getSecret(),
				tag);
	}
}
