package net.sf.briar.plugins.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.StreamConnection;

import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;

class BluetoothTransportConnection implements DuplexTransportConnection {

	private final StreamConnection stream;
	private final long maxLatency;

	BluetoothTransportConnection(StreamConnection stream, long maxLatency) {
		this.stream = stream;
		this.maxLatency = maxLatency;
	}

	public long getMaxLatency() {
		return maxLatency;
	}

	public InputStream getInputStream() throws IOException {
		return stream.openInputStream();
	}

	public OutputStream getOutputStream() throws IOException {
		return stream.openOutputStream();
	}

	public boolean shouldFlush() {
		return true;
	}

	public void dispose(boolean exception, boolean recognised)
			throws IOException {
		stream.close();
	}
}
