package org.briarproject.plugins.droidtooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.TransportConnectionReader;
import org.briarproject.api.plugins.TransportConnectionWriter;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;

import android.bluetooth.BluetoothSocket;

class DroidtoothTransportConnection implements DuplexTransportConnection {

	private final Plugin plugin;
	private final BluetoothSocket socket;
	private final Reader reader;
	private final Writer writer;
	private final AtomicBoolean halfClosed, closed;

	DroidtoothTransportConnection(Plugin plugin, BluetoothSocket socket) {
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

		public int getMaxFrameLength() {
			return plugin.getMaxFrameLength();
		}

		public long getMaxLatency() {
			return plugin.getMaxLatency();
		}

		public InputStream getInputStream() throws IOException {
			return socket.getInputStream();
		}

		public void dispose(boolean exception, boolean recognised)
				throws IOException {
			if(halfClosed.getAndSet(true) || exception)
				if(!closed.getAndSet(true)) socket.close();
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
			return socket.getOutputStream();
		}

		public void dispose(boolean exception) throws IOException {
			if(halfClosed.getAndSet(true) || exception)
				if(!closed.getAndSet(true)) socket.close();
		}
	}
}
