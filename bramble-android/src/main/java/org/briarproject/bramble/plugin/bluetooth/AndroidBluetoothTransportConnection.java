package org.briarproject.bramble.plugin.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import org.briarproject.bramble.api.io.TimeoutMonitor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.duplex.AbstractDuplexTransportConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static android.content.Context.POWER_SERVICE;
import static android.os.PowerManager.PARTIAL_WAKE_LOCK;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.bramble.api.plugin.BluetoothConstants.PROP_ADDRESS;
import static org.briarproject.bramble.util.AndroidUtils.isValidBluetoothAddress;

@NotNullByDefault
class AndroidBluetoothTransportConnection
		extends AbstractDuplexTransportConnection {

	private static final String WAKE_LOCK_TAG =
			"org.briarproject.briar.android:bluetooth";

	private final BluetoothConnectionLimiter connectionLimiter;
	private final BluetoothSocket socket;
	private final InputStream in;
	private final WakeLock wakeLock;

	@SuppressLint("WakelockTimeout")
	AndroidBluetoothTransportConnection(Plugin plugin,
			BluetoothConnectionLimiter connectionLimiter,
			TimeoutMonitor timeoutMonitor, Context appContext,
			BluetoothSocket socket) throws IOException {
		super(plugin);
		this.connectionLimiter = connectionLimiter;
		this.socket = socket;
		in = timeoutMonitor.createTimeoutInputStream(
				socket.getInputStream(), plugin.getMaxIdleTime() * 2);
		PowerManager pm = (PowerManager)
				requireNonNull(appContext.getSystemService(POWER_SERVICE));
		wakeLock = pm.newWakeLock(PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
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
