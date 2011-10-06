package net.sf.briar.plugins.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import net.sf.briar.api.transport.stream.StreamTransportConnection;

class SocketTransportConnection implements StreamTransportConnection {

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

	public void dispose(boolean success) throws IOException {
		socket.close();
	}
}
