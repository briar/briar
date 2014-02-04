package org.briarproject.plugins.droidtooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;

import android.bluetooth.BluetoothSocket;

class DroidtoothTransportConnection implements DuplexTransportConnection {

	private final Plugin plugin;
	private final BluetoothSocket socket;

	DroidtoothTransportConnection(Plugin plugin, BluetoothSocket socket) {
		this.plugin = plugin;
		this.socket = socket;
	}

	public int getMaxFrameLength() {
		return plugin.getMaxFrameLength();
	}

	public long getMaxLatency() {
		return plugin.getMaxLatency();
	}

	public InputStream getInputStream() throws IOException {
		return socket.getInputStream();
	}

	public OutputStream getOutputStream() throws IOException {
		return socket.getOutputStream();
	}

	public void dispose(boolean exception, boolean recognised)
			throws IOException {
		socket.close();
	}
}
