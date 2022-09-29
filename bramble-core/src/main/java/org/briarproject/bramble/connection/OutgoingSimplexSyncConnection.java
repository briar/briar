package org.briarproject.bramble.connection;

import org.briarproject.bramble.api.connection.ConnectionRegistry;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.sync.OutgoingSessionRecord;
import org.briarproject.bramble.api.sync.SyncSession;
import org.briarproject.bramble.api.sync.SyncSessionFactory;
import org.briarproject.bramble.api.transport.KeyManager;
import org.briarproject.bramble.api.transport.StreamContext;
import org.briarproject.bramble.api.transport.StreamReaderFactory;
import org.briarproject.bramble.api.transport.StreamWriter;
import org.briarproject.bramble.api.transport.StreamWriterFactory;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.IOException;

import javax.annotation.Nullable;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.nullsafety.NullSafety.requireNonNull;

@NotNullByDefault
class OutgoingSimplexSyncConnection extends SyncConnection implements Runnable {

	private final ContactId contactId;
	private final TransportId transportId;
	private final TransportConnectionWriter writer;
	@Nullable
	private final OutgoingSessionRecord sessionRecord;

	OutgoingSimplexSyncConnection(KeyManager keyManager,
			ConnectionRegistry connectionRegistry,
			StreamReaderFactory streamReaderFactory,
			StreamWriterFactory streamWriterFactory,
			SyncSessionFactory syncSessionFactory,
			TransportPropertyManager transportPropertyManager,
			ContactId contactId, TransportId transportId,
			TransportConnectionWriter writer,
			@Nullable OutgoingSessionRecord sessionRecord) {
		super(keyManager, connectionRegistry, streamReaderFactory,
				streamWriterFactory, syncSessionFactory,
				transportPropertyManager);
		this.contactId = contactId;
		this.transportId = transportId;
		this.writer = writer;
		this.sessionRecord = sessionRecord;
	}

	@Override
	public void run() {
		// Allocate a stream context
		StreamContext ctx = allocateStreamContext(contactId, transportId);
		if (ctx == null) {
			LOG.warning("Could not allocate stream context");
			onError();
			return;
		}
		try {
			// Create and run the outgoing session
			createSimplexOutgoingSession(ctx, writer).run();
			writer.dispose(false);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			onError();
		}
	}

	private void onError() {
		disposeOnError(writer);
	}

	private SyncSession createSimplexOutgoingSession(StreamContext ctx,
			TransportConnectionWriter w) throws IOException {
		StreamWriter streamWriter = streamWriterFactory.createStreamWriter(
				w.getOutputStream(), ctx);
		ContactId c = requireNonNull(ctx.getContactId());
		if (sessionRecord == null) {
			// Use eager retransmission if the transport is lossy and cheap
			return syncSessionFactory.createSimplexOutgoingSession(c,
					ctx.getTransportId(), w.getMaxLatency(),
					w.isLossyAndCheap(), streamWriter);
		} else {
			return syncSessionFactory.createSimplexOutgoingSession(c,
					ctx.getTransportId(), w.getMaxLatency(), streamWriter,
					sessionRecord);
		}
	}
}

