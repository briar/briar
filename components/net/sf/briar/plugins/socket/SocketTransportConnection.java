package net.sf.briar.plugins.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.transport.StreamTransportConnection;

class SocketTransportConnection implements StreamTransportConnection {

	private static final Logger LOG =
		Logger.getLogger(SocketTransportConnection.class.getName());

	private final Socket socket;

	SocketTransportConnection(Socket socket) {
		this.socket = socket;
	}

	public InputStream getInputStream() throws IOException {
		return socket.getInputStream();
	}

	public OutputStream getOutputStream() throws IOException {
		return socket.getOutputStream();
	}

	public void dispose(boolean success) {
		try {
			socket.close();
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
		}
	}
}
