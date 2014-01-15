package org.briarproject.plugins.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.StreamConnection;

import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;

class BluetoothTransportConnection implements DuplexTransportConnection {

	private final Plugin plugin;
	private final StreamConnection stream;

	BluetoothTransportConnection(Plugin plugin, StreamConnection stream) {
		this.plugin = plugin;
		this.stream = stream;
	}

	public int getMaxFrameLength() {
		return plugin.getMaxFrameLength();
	}

	public long getMaxLatency() {
		return plugin.getMaxLatency();
	}

	public InputStream getInputStream() throws IOException {
		return stream.openInputStream();
	}

	public OutputStream getOutputStream() throws IOException {
		return stream.openOutputStream();
	}

	public void dispose(boolean exception, boolean recognised)
			throws IOException {
		stream.close();
	}
}
