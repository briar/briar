package org.briarproject.bramble.plugin.bluetooth;

import android.bluetooth.BluetoothSocket;

import org.briarproject.bramble.api.io.TimeoutMonitor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.duplex.AbstractDuplexTransportConnection;
import org.briarproject.bramble.api.system.AndroidWakeLock;
import org.briarproject.bramble.api.system.AndroidWakeLockManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_ADDRESS;
import static org.briarproject.bramble.util.AndroidUtils.isValidBluetoothAddress;

@NotNullByDefault
class AndroidBluetoothTransportConnection
		extends AbstractDuplexTransportConnection {

	private final BluetoothConnectionLimiter connectionLimiter;
	private final BluetoothSocket socket;
	private final InputStream in;
	private final AndroidWakeLock wakeLock;

	AndroidBluetoothTransportConnection(Plugin plugin,
			BluetoothConnectionLimiter connectionLimiter,
			AndroidWakeLockManager wakeLockManager,
			TimeoutMonitor timeoutMonitor,
			BluetoothSocket socket) throws IOException {
		super(plugin);
		this.connectionLimiter = connectionLimiter;
		this.socket = socket;
		in = timeoutMonitor.createTimeoutInputStream(
				socket.getInputStream(), plugin.getMaxIdleTime() * 2);
		wakeLock = wakeLockManager.createWakeLock("BluetoothConnection");
		wakeLock.acquire();
		String address = socket.getRemoteDevice().getAddress();
		if (isValidBluetoothAddress(address)) remote.put(PROP_ADDRESS, address);
	}

	@Override
	protected InputStream getInputStream() {
		return in;
	}

	@Override
	protected OutputStream getOutputStream() throws IOException {
		return socket.getOutputStream();
	}

	@Override
	protected void closeConnection(boolean exception) throws IOException {
		try {
			socket.close();
			in.close();
		} finally {
			wakeLock.release();
			connectionLimiter.connectionClosed(this);
		}
	}
}
