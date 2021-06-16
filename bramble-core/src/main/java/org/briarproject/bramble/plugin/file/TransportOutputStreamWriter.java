package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;

import java.io.OutputStream;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.IoUtils.tryToClose;

@NotNullByDefault
class TransportOutputStreamWriter implements TransportConnectionWriter {

	private static final Logger LOG =
			getLogger(TransportOutputStreamWriter.class.getName());

	private final SimplexPlugin plugin;
	private final OutputStream out;

	TransportOutputStreamWriter(SimplexPlugin plugin, OutputStream out) {
		this.plugin = plugin;
		this.out = out;
	}

	@Override
	public int getMaxLatency() {
		return plugin.getMaxLatency();
	}

	@Override
	public int getMaxIdleTime() {
		return plugin.getMaxIdleTime();
	}

	@Override
	public boolean isLossyAndCheap() {
		return plugin.isLossyAndCheap();
	}

	@Override
	public OutputStream getOutputStream() {
		return out;
	}

	@Override
	public void dispose(boolean exception) {
		tryToClose(out, LOG, WARNING);
	}
}
