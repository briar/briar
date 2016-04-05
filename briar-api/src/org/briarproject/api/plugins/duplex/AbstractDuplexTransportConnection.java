package org.briarproject.api.plugins.duplex;

import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.TransportConnectionReader;
import org.briarproject.api.plugins.TransportConnectionWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

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

		public InputStream getInputStream() throws IOException {
			return AbstractDuplexTransportConnection.this.getInputStream();
		}

		public void dispose(boolean exception, boolean recognised)
				throws IOException {
			if (halfClosed.getAndSet(true) || exception || !recognised)
				if (!closed.getAndSet(true)) closeConnection(exception);
		}
	}

	private class Writer implements TransportConnectionWriter {

		public int getMaxLatency() {
			return plugin.getMaxLatency();
		}

		public int getMaxIdleTime() {
			return plugin.getMaxIdleTime();
		}

		public long getCapacity() {
			return Long.MAX_VALUE;
		}

		public OutputStream getOutputStream() throws IOException {
			return AbstractDuplexTransportConnection.this.getOutputStream();
		}

		public void dispose(boolean exception) throws IOException {
			if (halfClosed.getAndSet(true) || exception)
				if (!closed.getAndSet(true)) closeConnection(exception);
		}
	}
}
