package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.contact.ContactExchangeManager;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.HandshakeManager;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionManager;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.sync.SyncSessionFactory;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.api.transport.StreamReaderFactory;
import org.briarproject.bramble.api.transport.StreamWriterFactory;

import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class ConnectionFactoryImpl implements ConnectionFactory {

	private final Executor ioExecutor;
	private final KeyManager keyManager;
	private final StreamReaderFactory streamReaderFactory;
	private final StreamWriterFactory streamWriterFactory;
	private final SyncSessionFactory syncSessionFactory;
	private final HandshakeManager handshakeManager;
	private final ContactExchangeManager contactExchangeManager;
	private final ConnectionRegistry connectionRegistry;
	private final TransportPropertyManager transportPropertyManager;

	@Inject
	ConnectionFactoryImpl(@IoExecutor Executor ioExecutor,
			KeyManager keyManager, StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory,
			SyncSessionFactory syncSessionFactory,
			HandshakeManager handshakeManager,
			ContactExchangeManager contactExchangeManager,
			ConnectionRegistry connectionRegistry,
			TransportPropertyManager transportPropertyManager) {
		this.ioExecutor = ioExecutor;
		this.keyManager = keyManager;
		this.streamReaderFactory = streamReaderFactory;
		this.streamWriterFactory = streamWriterFactory;
		this.syncSessionFactory = syncSessionFactory;
		this.handshakeManager = handshakeManager;
		this.contactExchangeManager = contactExchangeManager;
		this.connectionRegistry = connectionRegistry;
		this.transportPropertyManager = transportPropertyManager;
	}

	@Override
	public Runnable createIncomingSimplexSyncConnection(TransportId t,
			TransportConnectionReader r) {
		return new IncomingSimplexSyncConnection(keyManager,
				connectionRegistry, streamReaderFactory, streamWriterFactory,
				syncSessionFactory, transportPropertyManager, t, r);
	}

	@Override
	public Runnable createIncomingDuplexSyncConnection(TransportId t,
			DuplexTransportConnection d) {
		return new IncomingDuplexSyncConnection(keyManager, connectionRegistry,
				streamReaderFactory, streamWriterFactory, syncSessionFactory,
				transportPropertyManager, ioExecutor, t, d);
	}

	@Override
	public Runnable createIncomingHandshakeConnection(
			ConnectionManager connectionManager, PendingContactId p,
			TransportId t, DuplexTransportConnection d) {
		return new IncomingHandshakeConnection(keyManager, connectionRegistry,
				streamReaderFactory, streamWriterFactory, handshakeManager,
				contactExchangeManager, connectionManager, p, t, d);
	}

	@Override
	public Runnable createOutgoingSimplexSyncConnection(ContactId c,
			TransportId t, TransportConnectionWriter w) {
		return new OutgoingSimplexSyncConnection(keyManager,
				connectionRegistry, streamReaderFactory, streamWriterFactory,
				syncSessionFactory, transportPropertyManager, c, t, w);
	}

	@Override
	public Runnable createOutgoingDuplexSyncConnection(ContactId c,
			TransportId t, DuplexTransportConnection d) {
		return new OutgoingDuplexSyncConnection(keyManager, connectionRegistry,
				streamReaderFactory, streamWriterFactory, syncSessionFactory,
				transportPropertyManager, ioExecutor, c, t, d);
	}

	@Override
	public Runnable createOutgoingHandshakeConnection(
			ConnectionManager connectionManager, PendingContactId p,
			TransportId t, DuplexTransportConnection d) {
		return new OutgoingHandshakeConnection(keyManager, connectionRegistry,
				streamReaderFactory, streamWriterFactory, handshakeManager,
				contactExchangeManager, connectionManager, p, t, d);
	}
}
