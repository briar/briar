package org.briarproject.bramble.plugin.bluetooth;

import android.bluetooth.BluetoothSocket;

import org.briarproject.bramble.api.io.TimeoutMonitor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.system.AndroidWakeLockFactory;

import java.io.IOException;

@NotNullByDefault
class AndroidBluetoothConnectionFactory
		implements BluetoothConnectionFactory<BluetoothSocket> {

	private final BluetoothConnectionLimiter connectionLimiter;
	private final AndroidWakeLockFactory wakeLockFactory;
	private final TimeoutMonitor timeoutMonitor;

	AndroidBluetoothConnectionFactory(
			BluetoothConnectionLimiter connectionLimiter,
			AndroidWakeLockFactory wakeLockFactory,
			TimeoutMonitor timeoutMonitor) {
		this.connectionLimiter = connectionLimiter;
		this.wakeLockFactory = wakeLockFactory;
		this.timeoutMonitor = timeoutMonitor;
	}

	@Override
	public DuplexTransportConnection wrapSocket(DuplexPlugin plugin,
			BluetoothSocket s) throws IOException {
		return new AndroidBluetoothTransportConnection(plugin,
				connectionLimiter, wakeLockFactory, timeoutMonitor, s);
	}
}
