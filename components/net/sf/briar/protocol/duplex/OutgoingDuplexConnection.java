package net.sf.briar.protocol.duplex;

import java.io.IOException;
import java.util.concurrent.Executor;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.plugins.DuplexTransportConnection;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.protocol.VerificationExecutor;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;

class OutgoingDuplexConnection extends DuplexConnection {

	private final TransportIndex transportIndex;

	private ConnectionContext ctx = null; // Locking: this

	OutgoingDuplexConnection(@DatabaseExecutor Executor dbExecutor,
			@VerificationExecutor Executor verificationExecutor,
			DatabaseComponent db, ConnectionRegistry connRegistry,
			ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory,
			ProtocolReaderFactory protoReaderFactory,
			ProtocolWriterFactory protoWriterFactory, ContactId contactId,
			TransportId transportId, TransportIndex transportIndex,
			DuplexTransportConnection transport) {
		super(dbExecutor, verificationExecutor, db, connRegistry,
				connReaderFactory, connWriterFactory, protoReaderFactory,
				protoWriterFactory, contactId, transportId, transport);
		this.transportIndex = transportIndex;
	}

	@Override
	protected ConnectionReader createConnectionReader() throws DbException,
	IOException {
		synchronized(this) {
			if(ctx == null)
				ctx = db.getConnectionContext(contactId, transportIndex);
		}
		return connReaderFactory.createConnectionReader(
				transport.getInputStream(), ctx.getSecret());
	}

	@Override
	protected ConnectionWriter createConnectionWriter() throws DbException,
	IOException {
		synchronized(this) {
			if(ctx == null)
				ctx = db.getConnectionContext(contactId, transportIndex);
		}
		return connWriterFactory.createConnectionWriter(
				transport.getOutputStream(), Long.MAX_VALUE, ctx.getSecret());
	}
}
