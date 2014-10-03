package org.briarproject.messaging.duplex;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;

import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.messaging.MessageVerifier;
import org.briarproject.api.messaging.PacketReaderFactory;
import org.briarproject.api.messaging.PacketWriterFactory;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.transport.ConnectionContext;
import org.briarproject.api.transport.ConnectionReader;
import org.briarproject.api.transport.ConnectionReaderFactory;
import org.briarproject.api.transport.ConnectionRegistry;
import org.briarproject.api.transport.ConnectionWriter;
import org.briarproject.api.transport.ConnectionWriterFactory;

class OutgoingDuplexConnection extends DuplexConnection {

	OutgoingDuplexConnection(Executor dbExecutor, Executor cryptoExecutor,
			MessageVerifier messageVerifier, DatabaseComponent db,
			EventBus eventBus, ConnectionRegistry connRegistry,
			ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory,
			PacketReaderFactory packetReaderFactory,
			PacketWriterFactory packetWriterFactory, ConnectionContext ctx,
			DuplexTransportConnection transport) {
		super(dbExecutor, cryptoExecutor, messageVerifier, db, eventBus,
				connRegistry, connReaderFactory, connWriterFactory,
				packetReaderFactory, packetWriterFactory, ctx, transport);
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
