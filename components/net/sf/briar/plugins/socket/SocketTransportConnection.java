package net.sf.briar.plugins.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import net.sf.briar.api.transport.stream.StreamTransportConnection;

class SocketTransportConnection implements StreamTransportConnection {

	private final Socket socket;

	private boolean streamInUse = false;

	SocketTransportConnection(Socket socket) {
		this.socket = socket;
	}

	public InputStream getInputStream() throws IOException {
		streamInUse = true;
		return socket.getInputStream();
	}

	public OutputStream getOutputStream() throws IOException {
		streamInUse = true;
		return socket.getOutputStream();
	}

	public void finish() throws IOException {
		// FIXME: Tell the plugin?
		streamInUse = false;
	}

	public void dispose() throws IOException {
		if(streamInUse) socket.close();
	}
}
