package net.sf.briar.messaging.duplex;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;

import net.sf.briar.api.db.DatabaseComponent;
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

	OutgoingDuplexConnection(Executor dbExecutor, Executor cryptoExecutor,
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
		InputStream in = transport.getInputStream();
		int maxFrameLength = transport.getMaxFrameLength();
		return connReaderFactory.createConnectionReader(in, maxFrameLength,
				ctx, false, false);
	}

	@Override
	protected ConnectionWriter createConnectionWriter() throws IOException {
		OutputStream out = transport.getOutputStream();
		int maxFrameLength = transport.getMaxFrameLength();
		return connWriterFactory.createConnectionWriter(out, maxFrameLength,
				Long.MAX_VALUE, ctx, false, true);
	}
}
