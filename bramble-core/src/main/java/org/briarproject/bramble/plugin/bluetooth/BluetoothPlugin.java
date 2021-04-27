package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

import javax.annotation.Nullable;

@NotNullByDefault
public interface BluetoothPlugin extends DuplexPlugin {

	boolean isDiscovering();

	@Nullable
	DuplexTransportConnection discoverAndConnectForSetup(String uuid);

	void stopDiscoverAndConnect();
}
