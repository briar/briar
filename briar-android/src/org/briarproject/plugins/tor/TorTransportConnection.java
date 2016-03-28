package org.briarproject.plugins.tor;

import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.TransportConnectionReader;
import org.briarproject.api.plugins.TransportConnectionWriter;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

class TorTransportConnection implements DuplexTransportConnection {

	private final Plugin plugin;
	private final Socket socket;
	private final Reader reader;
	private final Writer writer;
	private final AtomicBoolean halfClosed, closed;

	TorTransportConnection(Plugin plugin, Socket socket) {
		this.plugin = plugin;
		this.socket = socket;
		reader = new Reader();
		writer = new Writer();
		halfClosed = new AtomicBoolean(false);
		closed = new AtomicBoolean(false);
	}

	public TransportConnectionReader getReader() {
		return reader;
	}

	public TransportConnectionWriter getWriter() {
		return writer;
	}

	private class Reader implements TransportConnectionReader {

		public InputStream getInputStream() throws IOException {
			return socket.getInputStream();
		}

		public void dispose(boolean exception, boolean recognised)
				throws IOException {
			if (halfClosed.getAndSet(true) || exception)
				if (!closed.getAndSet(true)) socket.close();
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
			return socket.getOutputStream();
		}

		public void dispose(boolean exception) throws IOException {
			if (halfClosed.getAndSet(true) || exception)
				if (!closed.getAndSet(true)) socket.close();
		}
	}
}
