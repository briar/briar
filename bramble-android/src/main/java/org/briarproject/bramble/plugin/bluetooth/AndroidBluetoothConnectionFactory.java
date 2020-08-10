package org.briarproject.bramble.plugin.bluetooth;

import android.bluetooth.BluetoothSocket;

import org.briarproject.bramble.api.io.TimeoutMonitor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.system.AndroidWakeLockManager;

import java.io.IOException;

@NotNullByDefault
class AndroidBluetoothConnectionFactory
		implements BluetoothConnectionFactory<BluetoothSocket> {

	private final BluetoothConnectionLimiter connectionLimiter;
	private final AndroidWakeLockManager wakeLockManager;
	private final TimeoutMonitor timeoutMonitor;

	AndroidBluetoothConnectionFactory(
			BluetoothConnectionLimiter connectionLimiter,
			AndroidWakeLockManager wakeLockManager,
			TimeoutMonitor timeoutMonitor) {
		this.connectionLimiter = connectionLimiter;
		this.wakeLockManager = wakeLockManager;
		this.timeoutMonitor = timeoutMonitor;
	}

	@Override
	public DuplexTransportConnection wrapSocket(DuplexPlugin plugin,
			BluetoothSocket s) throws IOException {
		return new AndroidBluetoothTransportConnection(plugin,
				connectionLimiter, wakeLockManager, timeoutMonitor, s);
	}
}
