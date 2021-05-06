package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.ConnectionHandler;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.io.File;
import java.util.Collection;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;

import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.RemovableDriveConstants.ID;

@Immutable
@NotNullByDefault
class RemovableDrivePlugin extends FilePlugin {

	private static final Logger LOG =
			getLogger(RemovableDrivePlugin.class.getName());

	RemovableDrivePlugin(PluginCallback callback, int maxLatency) {
		super(callback, maxLatency);
	}

	@Override
	protected void writerFinished(File f, boolean exception) {
		if (LOG.isLoggable(INFO)) {
			LOG.info("Writer finished, exception: " + exception);
		}
	}

	@Override
	protected void readerFinished(File f, boolean exception,
			boolean recognised) {
		if (LOG.isLoggable(INFO)) {
			LOG.info("Reader finished, exception: " + exception
					+ ", recognised: " + recognised);
		}
		// Try to delete the file if the read finished successfully
		if (recognised && !exception && !f.delete()) {
			LOG.info("Failed to delete recognised file");
		}
	}

	@Override
	public TransportId getId() {
		return ID;
	}

	@Override
	public void start() {
	}

	@Override
	public void stop() {
	}

	@Override
	public State getState() {
		return ACTIVE;
	}

	@Override
	public int getReasonsDisabled() {
		return 0;
	}

	@Override
	public int getMaxIdleTime() {
		// Unused for simplex transports
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean shouldPoll() {
		return false;
	}

	@Override
	public int getPollingInterval() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void poll(
			Collection<Pair<TransportProperties, ConnectionHandler>> properties) {
		throw new UnsupportedOperationException();
	}
}
