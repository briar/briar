package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.io.TimeoutMonitor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.duplex.AbstractDuplexTransportConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.StreamConnection;

@NotNullByDefault
class JavaBluetoothTransportConnection
		extends AbstractDuplexTransportConnection {

	private final BluetoothConnectionLimiter connectionLimiter;
	private final StreamConnection socket;
	private final InputStream in;
	private final OutputStream out;

	JavaBluetoothTransportConnection(Plugin plugin,
			BluetoothConnectionLimiter connectionLimiter,
			TimeoutMonitor timeoutMonitor,
			StreamConnection socket) throws IOException {
		super(plugin);
		this.connectionLimiter = connectionLimiter;
		this.socket = socket;
		in = timeoutMonitor.createTimeoutInputStream(
				socket.openInputStream(), plugin.getMaxIdleTime() * 2);
		out = socket.openOutputStream();
	}

	@Override
	protected InputStream getInputStream() {
		return in;
	}

	@Override
	protected OutputStream getOutputStream() {
		return out;
	}

	@Override
	protected void closeConnection(boolean exception) throws IOException {
		try {
			socket.close();
			in.close();
		} finally {
			connectionLimiter.connectionClosed(this);
		}
	}
}
