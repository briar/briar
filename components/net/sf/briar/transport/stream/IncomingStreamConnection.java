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

public class IncomingStreamConnection extends StreamConnection {

	private final byte[] encryptedIv;

	IncomingStreamConnection(ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory, DatabaseComponent db,
			ProtocolReaderFactory protoReaderFactory,
			ProtocolWriterFactory protoWriterFactory, TransportId transportId,
			ContactId contactId, StreamTransportConnection connection,
			byte[] encryptedIv) {
		super(connReaderFactory, connWriterFactory, db, protoReaderFactory,
				protoWriterFactory, transportId, contactId, connection);
		this.encryptedIv = encryptedIv;
	}

	@Override
	protected ConnectionReader createConnectionReader() throws DbException,
	IOException {
		byte[] secret = db.getSharedSecret(contactId);
		return connReaderFactory.createConnectionReader(
				connection.getInputStream(), encryptedIv, secret);
	}

	@Override
	protected ConnectionWriter createConnectionWriter() throws DbException,
	IOException {
		byte[] secret = db.getSharedSecret(contactId);
		return connWriterFactory.createConnectionWriter(
				connection.getOutputStream(), Long.MAX_VALUE, transportId,
				encryptedIv, secret);
	}
}
