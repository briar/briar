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
	private final StreamConnection stream;
	private final InputStream in;

	JavaBluetoothTransportConnection(Plugin plugin,
			BluetoothConnectionLimiter connectionLimiter,
			TimeoutMonitor timeoutMonitor,
			StreamConnection stream) throws IOException {
		super(plugin);
		this.connectionLimiter = connectionLimiter;
		this.stream = stream;
		in = timeoutMonitor.createTimeoutInputStream(
				stream.openInputStream(), plugin.getMaxIdleTime() * 2);
	}

	@Override
	protected InputStream getInputStream() {
		return in;
	}

	@Override
	protected OutputStream getOutputStream() throws IOException {
		return stream.openOutputStream();
	}

	@Override
	protected void closeConnection(boolean exception) throws IOException {
		try {
			stream.close();
		} finally {
			connectionLimiter.connectionClosed(this);
		}
	}
}
