package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.connection.ConnectionManager;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.sync.event.MessagesSentEvent;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.file.RemovableDriveConstants.ID;

@NotNullByDefault
class RemovableDriveWriterTask extends RemovableDriveTaskImpl
		implements EventListener {

	private static final Logger LOG =
			getLogger(RemovableDriveWriterTask.class.getName());

	private final DatabaseComponent db;

	RemovableDriveWriterTask(
			DatabaseComponent db,
			Executor eventExecutor,
			PluginManager pluginManager,
			ConnectionManager connectionManager,
			EventBus eventBus,
			RemovableDriveTaskRegistry registry,
			ContactId contactId,
			File file) {
		super(eventExecutor, pluginManager, connectionManager, eventBus,
				registry, contactId, file);
		this.db = db;
	}

	@Override
	public void run() {
		TransportConnectionWriter w =
				getPlugin().createWriter(createProperties());
		if (w == null) {
			LOG.warning("Failed to create writer");
			registry.removeWriter(contactId, this);
			setSuccess(false);
			return;
		}
		// TODO: Get total bytes to send from DB
		eventBus.addListener(this);
		connectionManager.manageOutgoingConnection(contactId, ID,
				new DecoratedWriter(w));
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof MessagesSentEvent) {
			MessagesSentEvent m = (MessagesSentEvent) e;
			if (contactId.equals(m.getContactId())) {
				if (LOG.isLoggable(INFO)) {
					LOG.info(m.getMessageIds().size() + " messages sent");
				}
				// TODO: Update progress
			}
		}
	}

	private class DecoratedWriter implements TransportConnectionWriter {

		private final TransportConnectionWriter delegate;

		private DecoratedWriter(TransportConnectionWriter delegate) {
			this.delegate = delegate;
		}

		@Override
		public int getMaxLatency() {
			return delegate.getMaxLatency();
		}

		@Override
		public int getMaxIdleTime() {
			return delegate.getMaxIdleTime();
		}

		@Override
		public OutputStream getOutputStream() throws IOException {
			return delegate.getOutputStream();
		}

		@Override
		public void dispose(boolean exception) throws IOException {
			delegate.dispose(exception);
			registry.removeWriter(contactId, RemovableDriveWriterTask.this);
			eventBus.removeListener(RemovableDriveWriterTask.this);
			setSuccess(!exception);
		}
	}
}
