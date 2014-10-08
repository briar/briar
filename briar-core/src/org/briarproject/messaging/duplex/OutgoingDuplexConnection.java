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
import org.briarproject.api.transport.ConnectionRegistry;
import org.briarproject.api.transport.StreamContext;
import org.briarproject.api.transport.StreamReader;
import org.briarproject.api.transport.StreamReaderFactory;
import org.briarproject.api.transport.StreamWriter;
import org.briarproject.api.transport.StreamWriterFactory;

class OutgoingDuplexConnection extends DuplexConnection {

	OutgoingDuplexConnection(Executor dbExecutor, Executor cryptoExecutor,
			MessageVerifier messageVerifier, DatabaseComponent db,
			EventBus eventBus, ConnectionRegistry connRegistry,
			StreamReaderFactory connReaderFactory,
			StreamWriterFactory connWriterFactory,
			PacketReaderFactory packetReaderFactory,
			PacketWriterFactory packetWriterFactory, StreamContext ctx,
			DuplexTransportConnection transport) {
		super(dbExecutor, cryptoExecutor, messageVerifier, db, eventBus,
				connRegistry, connReaderFactory, connWriterFactory,
				packetReaderFactory, packetWriterFactory, ctx, transport);
	}

	@Override
	protected StreamReader createStreamReader() throws IOException {
		InputStream in = transport.getInputStream();
		int maxFrameLength = transport.getMaxFrameLength();
		return connReaderFactory.createStreamReader(in, maxFrameLength,
				ctx, false, false);
	}

	@Override
	protected StreamWriter createStreamWriter() throws IOException {
		OutputStream out = transport.getOutputStream();
		int maxFrameLength = transport.getMaxFrameLength();
		return connWriterFactory.createStreamWriter(out, maxFrameLength,
				Long.MAX_VALUE, ctx, false, true);
	}
}
