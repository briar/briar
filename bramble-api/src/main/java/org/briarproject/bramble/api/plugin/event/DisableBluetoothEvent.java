package org.briarproject.bramble.api.plugin.event;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.BluetoothEnableDisableReason;

import javax.annotation.concurrent.Immutable;

/**
 * An event that asks the Bluetooth plugin to disable the Bluetooth adapter if
 * we previously enabled it.
 */
@Immutable
@NotNullByDefault
public class DisableBluetoothEvent extends BluetoothEvent {
	private boolean force;

	public DisableBluetoothEvent(BluetoothEnableDisableReason reason) {
		super(reason);
		force = false;
	}

	public DisableBluetoothEvent(boolean force){
		super(null);
		this.force = force;
	}

	public boolean isForced(){
		return force;
	}
}
