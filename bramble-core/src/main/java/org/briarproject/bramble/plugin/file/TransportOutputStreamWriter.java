package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;

import java.io.OutputStream;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.IoUtils.tryToClose;

@NotNullByDefault
class TransportOutputStreamWriter implements TransportConnectionWriter {

	private static final Logger LOG =
			getLogger(TransportOutputStreamWriter.class.getName());

	private final Plugin plugin;
	private final OutputStream out;

	TransportOutputStreamWriter(Plugin plugin, OutputStream out) {
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
	public OutputStream getOutputStream() {
		return out;
	}

	@Override
	public void dispose(boolean exception) {
		tryToClose(out, LOG, WARNING);
	}
}
