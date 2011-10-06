package net.sf.briar.plugins.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.StreamConnection;

import net.sf.briar.api.transport.stream.StreamTransportConnection;

class BluetoothTransportConnection implements StreamTransportConnection {

	private final StreamConnection stream;

	BluetoothTransportConnection(StreamConnection stream) {
		this.stream = stream;
	}

	public InputStream getInputStream() throws IOException {
		return stream.openInputStream();
	}

	public OutputStream getOutputStream() throws IOException {
		return stream.openOutputStream();
	}

	public void dispose(boolean success) throws IOException {
		stream.close();
	}
}
