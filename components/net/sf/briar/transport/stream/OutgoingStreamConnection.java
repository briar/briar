package net.sf.briar.transport.stream;

import java.io.IOException;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.writers.ProtocolWriterFactory;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.StreamTransportConnection;

public class OutgoingStreamConnection extends StreamConnection {

	private final TransportId transportId;

	private long connectionNum = -1L;

	OutgoingStreamConnection(ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory, DatabaseComponent db,
			ProtocolReaderFactory protoReaderFactory,
			ProtocolWriterFactory protoWriterFactory, ContactId contactId,
			StreamTransportConnection connection, TransportId transportId) {
		super(connReaderFactory, connWriterFactory, db, protoReaderFactory,
				protoWriterFactory, contactId, connection);
		this.transportId = transportId;
	}

	@Override
	protected ConnectionReader createConnectionReader() throws DbException,
	IOException {
		if(connectionNum == -1L)
			connectionNum = db.getConnectionNumber(contactId, transportId);
		byte[] secret = db.getSharedSecret(contactId);
		return connReaderFactory.createConnectionReader(
				connection.getInputStream(), false, transportId, connectionNum,
				secret);
	}

	@Override
	protected ConnectionWriter createConnectionWriter() throws DbException,
	IOException {
		if(connectionNum == -1L)
			connectionNum = db.getConnectionNumber(contactId, transportId);
		byte[] secret = db.getSharedSecret(contactId);
		return connWriterFactory.createConnectionWriter(
				connection.getOutputStream(), Long.MAX_VALUE, true, transportId,
				connectionNum, secret);
	}
}
