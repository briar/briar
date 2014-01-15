package org.briarproject.plugins.tcp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.briarproject.api.plugins.Plugin;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;

class TcpTransportConnection implements DuplexTransportConnection {

	private final Plugin plugin;
	private final Socket socket;

	TcpTransportConnection(Plugin plugin, Socket socket) {
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
