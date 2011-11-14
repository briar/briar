package net.sf.briar.transport.stream;

import java.io.IOException;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.protocol.writers.ProtocolWriterFactory;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.api.transport.ConnectionWriterFactory;
import net.sf.briar.api.transport.StreamTransportConnection;

public class OutgoingStreamConnection extends StreamConnection {

	private long connectionNum = -1L; // Locking: this

	OutgoingStreamConnection(ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory, DatabaseComponent db,
			ProtocolReaderFactory protoReaderFactory,
			ProtocolWriterFactory protoWriterFactory,
			TransportIndex transportIndex, ContactId contactId,
			StreamTransportConnection connection) {
		super(connReaderFactory, connWriterFactory, db, protoReaderFactory,
				protoWriterFactory, transportIndex, contactId, connection);
	}

	@Override
	protected ConnectionReader createConnectionReader() throws DbException,
	IOException {
		synchronized(this) {
			if(connectionNum == -1L) {
				connectionNum = db.getConnectionNumber(contactId,
						transportIndex);
			}
		}
		byte[] secret = db.getSharedSecret(contactId);
		return connReaderFactory.createConnectionReader(
				connection.getInputStream(), transportIndex, connectionNum,
				secret);
	}

	@Override
	protected ConnectionWriter createConnectionWriter() throws DbException,
	IOException {
		synchronized(this) {
			if(connectionNum == -1L) {
				connectionNum = db.getConnectionNumber(contactId,
						transportIndex);
			}
		}
		byte[] secret = db.getSharedSecret(contactId);
		return connWriterFactory.createConnectionWriter(
				connection.getOutputStream(), Long.MAX_VALUE, transportIndex,
				connectionNum, secret);
	}
}
