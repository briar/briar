package net.sf.briar.plugins.tcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;

class TcpTransportConnection implements DuplexTransportConnection {

	private final Socket socket;
	private final long maxLatency;

	TcpTransportConnection(Socket socket, long maxLatency) {
		this.socket = socket;
		this.maxLatency = maxLatency;
	}

	public long getMaxLatency() {
		return maxLatency;
	}

	public InputStream getInputStream() throws IOException {
		return socket.getInputStream();
	}

	public OutputStream getOutputStream() throws IOException {
		return socket.getOutputStream();
	}

	public boolean shouldFlush() {
		return true;
	}

	public void dispose(boolean exception, boolean recognised)
			throws IOException {
		socket.close();
	}
}
