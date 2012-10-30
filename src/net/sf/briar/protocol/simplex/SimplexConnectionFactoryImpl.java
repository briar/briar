package net.sf.briar.protocol.simplex;

import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.crypto.KeyManager;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabaseExecutor;
import net.sf.briar.api.plugins.simplex.SimplexTransportReader;
import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;
import net.sf.briar.api.protocol.ProtocolReaderFactory;
import net.sf.briar.api.protocol.ProtocolWriterFactory;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.VerificationExecutor;
import net.sf.briar.api.protocol.simplex.SimplexConnectionFactory;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionReaderFactory;
import net.sf.briar.api.transport.ConnectionRegistry;
import net.sf.briar.api.transport.ConnectionWriterFactory;

import com.google.inject.Inject;

class SimplexConnectionFactoryImpl implements SimplexConnectionFactory {

	private static final Logger LOG =
			Logger.getLogger(SimplexConnectionFactoryImpl.class.getName());

	private final Executor dbExecutor, verificationExecutor;
	private final DatabaseComponent db;
	private final KeyManager keyManager;
	private final ConnectionRegistry connRegistry;
	private final ConnectionReaderFactory connReaderFactory;
	private final ConnectionWriterFactory connWriterFactory;
	private final ProtocolReaderFactory protoReaderFactory;
	private final ProtocolWriterFactory protoWriterFactory;

	@Inject
	SimplexConnectionFactoryImpl(@DatabaseExecutor Executor dbExecutor,
			@VerificationExecutor Executor verificationExecutor,
			DatabaseComponent db, KeyManager keyManager,
			ConnectionRegistry connRegistry,
			ConnectionReaderFactory connReaderFactory,
			ConnectionWriterFactory connWriterFactory,
			ProtocolReaderFactory protoReaderFactory,
			ProtocolWriterFactory protoWriterFactory) {
		this.dbExecutor = dbExecutor;
		this.verificationExecutor = verificationExecutor;
		this.db = db;
		this.keyManager = keyManager;
		this.connRegistry = connRegistry;
		this.connReaderFactory = connReaderFactory;
		this.connWriterFactory = connWriterFactory;
		this.protoReaderFactory = protoReaderFactory;
		this.protoWriterFactory = protoWriterFactory;
	}

	public void createIncomingConnection(ConnectionContext ctx, SimplexTransportReader r) {
		final IncomingSimplexConnection conn = new IncomingSimplexConnection(
				dbExecutor, verificationExecutor, db, connRegistry,
				connReaderFactory, protoReaderFactory, ctx, r);
		Runnable read = new Runnable() {
			public void run() {
				conn.read();
			}
		};
		new Thread(read).start();
	}

	public void createOutgoingConnection(ContactId c, TransportId t,
			SimplexTransportWriter w) {
		ConnectionContext ctx = keyManager.getConnectionContext(c, t);
		if(ctx == null) {
			if(LOG.isLoggable(Level.WARNING))
				LOG.warning("Could not create outgoing connection context");
			return;
		}		
		final OutgoingSimplexConnection conn = new OutgoingSimplexConnection(db,
				connRegistry, connWriterFactory, protoWriterFactory, ctx, w);
		Runnable write = new Runnable() {
			public void run() {
				conn.write();
			}
		};
		new Thread(write).start();
	}
}
