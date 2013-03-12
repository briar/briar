package net.sf.briar.messaging.duplex;

import java.io.IOException;
import java.util.concurrent.Executor;

import net.sf.briar.api.crypto.CryptoExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.messaging.MessageVerifier;
import net.sf.briar.api.messaging.PacketReaderFactory;
import net.sf.briar.api.messaging.PacketWriterFactory;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;

class OutgoingDuplexConnection extends DuplexConnection {

	OutgoingDuplexConnection(@DatabaseExecutor Executor dbExecutor,
			@CryptoExecutor Executor cryptoExecutor,
			MessageVerifier messageVerifier, DatabaseComponent db,
			ConnectionRegistry connRegistry,
			ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory,
			PacketReaderFactory packetReaderFactory,
			PacketWriterFactory packetWriterFactory, ConnectionContext ctx,
			DuplexTransportConnection transport) {
		super(dbExecutor, cryptoExecutor, messageVerifier, db, connRegistry,
				connReaderFactory, connWriterFactory, packetReaderFactory,
				packetWriterFactory, ctx, transport);
	}

	@Override
	protected ConnectionReader createConnectionReader() throws IOException {
		return connReaderFactory.createConnectionReader(
				transport.getInputStream(), ctx, false, false);
	}

	@Override
	protected ConnectionWriter createConnectionWriter() throws IOException {
		return connWriterFactory.createConnectionWriter(
				transport.getOutputStream(), Long.MAX_VALUE, ctx, false, true);
	}
}
