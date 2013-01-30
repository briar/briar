package net.sf.briar.messaging.duplex;

import java.io.IOException;
import java.util.concurrent.Executor;

import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.messaging.MessageVerifier;
import net.sf.briar.api.messaging.PacketReaderFactory;
import net.sf.briar.api.messaging.PacketWriterFactory;
import net.sf.briar.api.messaging.VerificationExecutor;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;

class IncomingDuplexConnection extends DuplexConnection {

	IncomingDuplexConnection(@DatabaseExecutor Executor dbExecutor,
			@VerificationExecutor Executor verificationExecutor,
			MessageVerifier messageVerifier, DatabaseComponent db,
			ConnectionRegistry connRegistry,
			ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory,
			PacketReaderFactory protoReaderFactory,
			PacketWriterFactory protoWriterFactory,
			ConnectionContext ctx, DuplexTransportConnection transport) {
		super(dbExecutor, verificationExecutor, messageVerifier, db,
				connRegistry, connReaderFactory, connWriterFactory,
				protoReaderFactory, protoWriterFactory, ctx, transport);
	}

	@Override
	protected ConnectionReader createConnectionReader() throws IOException {
		return connReaderFactory.createConnectionReader(
				transport.getInputStream(), ctx, true, true);
	}

	@Override
	protected ConnectionWriter createConnectionWriter() throws IOException {
		return connWriterFactory.createConnectionWriter(
				transport.getOutputStream(), Long.MAX_VALUE, ctx, true, false);
	}
}
