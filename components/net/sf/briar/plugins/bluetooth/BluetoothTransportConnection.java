package net.sf.briar.plugins.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.microedition.io.StreamConnection;

import net.sf.briar.api.transport.StreamTransportConnection;

class BluetoothTransportConnection implements StreamTransportConnection {

	private static final Logger LOG =
		Logger.getLogger(BluetoothTransportConnection.class.getName());

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

	public void dispose(boolean exception, boolean recognised) {
		try {
			stream.close();
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
		}
	}
}
