package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.connection.ConnectionManager;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.file.RemovableDriveConstants.ID;

@NotNullByDefault
class RemovableDriveReaderTask extends RemovableDriveTaskImpl {

	private final static Logger LOG =
			getLogger(RemovableDriveReaderTask.class.getName());

	RemovableDriveReaderTask(
			Executor eventExecutor,
			PluginManager pluginManager,
			ConnectionManager connectionManager,
			EventBus eventBus,
			RemovableDriveTaskRegistry registry,
			TransportProperties transportProperties) {
		super(eventExecutor, pluginManager, connectionManager, eventBus,
				registry, transportProperties);
	}

	@Override
	public void run() {
		TransportConnectionReader r =
				getPlugin().createReader(transportProperties);
		if (r == null) {
			LOG.warning("Failed to create reader");
			registry.removeReader(this);
			setSuccess(false);
			return;
		}
		connectionManager.manageIncomingConnection(ID, new DecoratedReader(r));
	}

	private class DecoratedReader implements TransportConnectionReader {

		private final TransportConnectionReader delegate;

		private DecoratedReader(TransportConnectionReader delegate) {
			this.delegate = delegate;
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return delegate.getInputStream();
		}

		@Override
		public void dispose(boolean exception, boolean recognised)
				throws IOException {
			delegate.dispose(exception, recognised);
			registry.removeReader(RemovableDriveReaderTask.this);
			setSuccess(!exception && recognised);
		}
	}
}
