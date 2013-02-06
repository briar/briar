package net.sf.briar.plugins.tor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;

import org.silvertunnel.netlib.api.NetSocket;

class TorTransportConnection implements DuplexTransportConnection {

	private final NetSocket socket;
	private final long maxLatency;

	TorTransportConnection(NetSocket socket, long maxLatency) {
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
