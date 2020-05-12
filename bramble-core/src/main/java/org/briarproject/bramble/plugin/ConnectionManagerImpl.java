package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionManager;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

import java.util.concurrent.Executor;

import javax.inject.Inject;

@NotNullByDefault
class ConnectionManagerImpl implements ConnectionManager {

	private final Executor ioExecutor;
	private final ConnectionFactory connectionFactory;

	@Inject
	ConnectionManagerImpl(@IoExecutor Executor ioExecutor,
			ConnectionFactory connectionFactory) {
		this.ioExecutor = ioExecutor;
		this.connectionFactory = connectionFactory;
	}

	@Override
	public void manageIncomingConnection(TransportId t,
			TransportConnectionReader r) {
		ioExecutor.execute(
				connectionFactory.createIncomingSimplexSyncConnection(t, r));
	}

	@Override
	public void manageIncomingConnection(TransportId t,
			DuplexTransportConnection d) {
		ioExecutor.execute(
				connectionFactory.createIncomingDuplexSyncConnection(t, d));
	}

	@Override
	public void manageIncomingConnection(PendingContactId p, TransportId t,
			DuplexTransportConnection d) {
		ioExecutor.execute(connectionFactory
				.createIncomingHandshakeConnection(this, p, t, d));
	}

	@Override
	public void manageOutgoingConnection(ContactId c, TransportId t,
			TransportConnectionWriter w) {
		ioExecutor.execute(
				connectionFactory.createOutgoingSimplexSyncConnection(c, t, w));
	}

	@Override
	public void manageOutgoingConnection(ContactId c, TransportId t,
			DuplexTransportConnection d) {
		ioExecutor.execute(
				connectionFactory.createOutgoingDuplexSyncConnection(c, t, d));
	}

	@Override
	public void manageOutgoingConnection(PendingContactId p, TransportId t,
			DuplexTransportConnection d) {
		ioExecutor.execute(connectionFactory
				.createOutgoingHandshakeConnection(this, p, t, d));
	}
}
