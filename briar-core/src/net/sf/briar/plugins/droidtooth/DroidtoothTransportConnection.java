package net.sf.briar.plugins.droidtooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;
import android.bluetooth.BluetoothSocket;

class DroidtoothTransportConnection implements DuplexTransportConnection {

	private final BluetoothSocket socket;
	private final long maxLatency;

	DroidtoothTransportConnection(BluetoothSocket socket, long maxLatency) {
		this.socket = socket;
		this.maxLatency = maxLatency;
	}

	public long getMaxLatency() {
		return maxLatency;
	}

	public InputStream getInputStream() throws IOException {
		return socket.getInputStream();
	}

	public OutputStream getOutputStream() throws IOException {
		return socket.getOutputStream();
	}

	public boolean shouldFlush() {
		return true;
	}

	public void dispose(boolean exception, boolean recognised)
			throws IOException {
		socket.close();
	}
}
