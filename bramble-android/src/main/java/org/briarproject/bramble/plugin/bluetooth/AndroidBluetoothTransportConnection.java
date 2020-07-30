package org.briarproject.bramble.plugin.bluetooth;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.PowerManager;

import org.briarproject.bramble.api.io.TimeoutMonitor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.duplex.AbstractDuplexTransportConnection;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.bramble.util.RenewableWakeLock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static android.content.Context.POWER_SERVICE;
import static android.os.PowerManager.PARTIAL_WAKE_LOCK;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_ADDRESS;
import static org.briarproject.bramble.util.AndroidUtils.getWakeLockTag;
import static org.briarproject.bramble.util.AndroidUtils.isValidBluetoothAddress;

@NotNullByDefault
class AndroidBluetoothTransportConnection
		extends AbstractDuplexTransportConnection {

	private final BluetoothConnectionLimiter connectionLimiter;
	private final RenewableWakeLock wakeLock;
	private final BluetoothSocket socket;
	private final InputStream in;

	AndroidBluetoothTransportConnection(Plugin plugin,
			BluetoothConnectionLimiter connectionLimiter,
			TimeoutMonitor timeoutMonitor,
			Context appContext,
			TaskScheduler scheduler,
			BluetoothSocket socket) throws IOException {
		super(plugin);
		this.connectionLimiter = connectionLimiter;
		this.socket = socket;
		in = timeoutMonitor.createTimeoutInputStream(
				socket.getInputStream(), plugin.getMaxIdleTime() * 2);
		PowerManager powerManager = (PowerManager)
				requireNonNull(appContext.getSystemService(POWER_SERVICE));
		String tag = getWakeLockTag(appContext);
		wakeLock = new RenewableWakeLock(powerManager, scheduler,
				PARTIAL_WAKE_LOCK, tag, 1, MINUTES);
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
		} finally {
			wakeLock.release();
			connectionLimiter.connectionClosed(this);
		}
	}
}
