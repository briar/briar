package net.sf.briar.plugins.tor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import net.sf.briar.api.plugins.Plugin;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;

class TorTransportConnection implements DuplexTransportConnection {

	private final Plugin plugin;
	private final Socket socket;

	TorTransportConnection(Plugin plugin, Socket socket) {
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

	public boolean shouldFlush() {
		return true;
	}

	public void dispose(boolean exception, boolean recognised)
			throws IOException {
		socket.close();
	}
}
