package net.sf.briar.plugins.tor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;

import org.silvertunnel.netlib.api.NetSocket;

class TorTransportConnection implements DuplexTransportConnection {

	private static final Logger LOG =
		Logger.getLogger(TorTransportConnection.class.getName());

	private final NetSocket socket;

	TorTransportConnection(NetSocket socket) {
		this.socket = socket;
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

	public void dispose(boolean exception, boolean recognised) {
		try {
			socket.close();
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
		}
	}
}
