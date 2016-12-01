package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.duplex.AbstractDuplexTransportConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.StreamConnection;

@NotNullByDefault
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
