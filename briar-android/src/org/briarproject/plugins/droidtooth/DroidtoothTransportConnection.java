package org.briarproject.plugins.droidtooth;

import android.bluetooth.BluetoothSocket;

import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.duplex.AbstractDuplexTransportConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class DroidtoothTransportConnection extends AbstractDuplexTransportConnection {

	private final BluetoothSocket socket;

	DroidtoothTransportConnection(Plugin plugin, BluetoothSocket socket) {
		super(plugin);
		this.socket = socket;
	}

	@Override
	protected InputStream getInputStream() throws IOException {
		return socket.getInputStream();
	}

	@Override
	protected OutputStream getOutputStream() throws IOException {
		return socket.getOutputStream();
	}

	@Override
	protected void closeConnection(boolean exception) throws IOException {
		socket.close();
	}
}
