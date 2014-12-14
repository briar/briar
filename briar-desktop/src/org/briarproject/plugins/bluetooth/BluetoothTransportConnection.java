package org.briarproject.plugins.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.io.StreamConnection;

import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.TransportConnectionReader;
import org.briarproject.api.plugins.TransportConnectionWriter;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;

class BluetoothTransportConnection implements DuplexTransportConnection {

	private final Plugin plugin;
	private final StreamConnection stream;
	private final Reader reader;
	private final Writer writer;
	private final AtomicBoolean halfClosed, closed;

	BluetoothTransportConnection(Plugin plugin, StreamConnection stream) {
		this.plugin = plugin;
		this.stream = stream;
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

		public int getMaxFrameLength() {
			return plugin.getMaxFrameLength();
		}

		public long getMaxLatency() {
			return plugin.getMaxLatency();
		}

		public InputStream getInputStream() throws IOException {
			return stream.openInputStream();
		}

		public void dispose(boolean exception, boolean recognised)
				throws IOException {
			if(halfClosed.getAndSet(true) || exception)
				if(!closed.getAndSet(true)) stream.close();
		}
	}

	private class Writer implements TransportConnectionWriter {

		public int getMaxFrameLength() {
			return plugin.getMaxFrameLength();
		}

		public long getMaxLatency() {
			return plugin.getMaxLatency();
		}

		public long getMaxIdleTime() {
			return plugin.getMaxIdleTime();
		}

		public long getCapacity() {
			return Long.MAX_VALUE;
		}

		public OutputStream getOutputStream() throws IOException {
			return stream.openOutputStream();
		}

		public void dispose(boolean exception) throws IOException {
			if(halfClosed.getAndSet(true) || exception)
				if(!closed.getAndSet(true)) stream.close();
		}
	}
}
