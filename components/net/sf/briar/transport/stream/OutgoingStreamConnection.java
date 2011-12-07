package net.sf.briar.transport.stream;

import java.io.IOException;
import java.util.concurrent.Executor;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.StreamTransportConnection;

class OutgoingStreamConnection extends StreamConnection {

	private final TransportIndex transportIndex;

	private ConnectionContext ctx = null; // Locking: this

	OutgoingStreamConnection(Executor executor, DatabaseComponent db,
			SerialComponent serial, ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory,
			ProtocolReaderFactory protoReaderFactory,
			ProtocolWriterFactory protoWriterFactory, ContactId contactId,
			TransportIndex transportIndex,
			StreamTransportConnection connection) {
		super(executor, db, serial, connReaderFactory, connWriterFactory,
				protoReaderFactory, protoWriterFactory, contactId, connection);
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
				connection.getInputStream(), ctx.getSecret());
	}

	@Override
	protected ConnectionWriter createConnectionWriter() throws DbException,
	IOException {
		synchronized(this) {
			if(ctx == null)
				ctx = db.getConnectionContext(contactId, transportIndex);
		}
		return connWriterFactory.createConnectionWriter(
				connection.getOutputStream(), Long.MAX_VALUE, ctx.getSecret());
	}
}
