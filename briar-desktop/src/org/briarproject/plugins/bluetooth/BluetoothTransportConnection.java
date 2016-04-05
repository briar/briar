package org.briarproject.plugins.bluetooth;

import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.duplex.AbstractDuplexTransportConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.StreamConnection;

class BluetoothTransportConnection extends AbstractDuplexTransportConnection {

	private final StreamConnection stream;

	BluetoothTransportConnection(Plugin plugin, StreamConnection stream) {
		super(plugin);
		this.stream = stream;
	}

	@Override
	protected InputStream getInputStream() throws IOException {
		return stream.openInputStream();
	}

	@Override
	protected OutputStream getOutputStream() throws IOException {
		return stream.openOutputStream();
	}

	@Override
	protected void closeConnection(boolean exception) throws IOException {
		stream.close();
	}
}
