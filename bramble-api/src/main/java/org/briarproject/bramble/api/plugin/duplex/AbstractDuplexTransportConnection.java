package org.briarproject.bramble.api.plugin.duplex;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

@NotNullByDefault
public abstract class AbstractDuplexTransportConnection
		implements DuplexTransportConnection {

	private final Plugin plugin;
	private final Reader reader;
	private final Writer writer;
	private final AtomicBoolean halfClosed, closed;

	protected AbstractDuplexTransportConnection(Plugin plugin) {
		this.plugin = plugin;
		reader = new Reader();
		writer = new Writer();
		halfClosed = new AtomicBoolean(false);
		closed = new AtomicBoolean(false);
	}

	protected abstract InputStream getInputStream() throws IOException;

	protected abstract OutputStream getOutputStream() throws IOException;

	protected abstract void closeConnection(boolean exception)
			throws IOException;

	@Override
	public TransportConnectionReader getReader() {
		return reader;
	}

	@Override
	public TransportConnectionWriter getWriter() {
		return writer;
	}

	private class Reader implements TransportConnectionReader {

		@Override
		public InputStream getInputStream() throws IOException {
			return AbstractDuplexTransportConnection.this.getInputStream();
		}

		@Override
		public void dispose(boolean exception, boolean recognised)
				throws IOException {
			if (halfClosed.getAndSet(true) || exception || !recognised)
				if (!closed.getAndSet(true)) closeConnection(exception);
		}
	}

	private class Writer implements TransportConnectionWriter {

		@Override
		public int getMaxLatency() {
			return plugin.getMaxLatency();
		}

		@Override
		public int getMaxIdleTime() {
			return plugin.getMaxIdleTime();
		}

		@Override
		public long getCapacity() {
			return Long.MAX_VALUE;
		}

		@Override
		public OutputStream getOutputStream() throws IOException {
			return AbstractDuplexTransportConnection.this.getOutputStream();
		}

		@Override
		public void dispose(boolean exception) throws IOException {
			if (halfClosed.getAndSet(true) || exception)
				if (!closed.getAndSet(true)) closeConnection(exception);
		}
	}
}
