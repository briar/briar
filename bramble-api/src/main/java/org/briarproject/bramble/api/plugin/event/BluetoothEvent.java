package org.briarproject.bramble.api.plugin.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.plugin.BluetoothEnableDisableReason;

/**
 * force disable stop the bluetooth adapter only when we turned it on
 */
abstract class BluetoothEvent extends Event {
	private BluetoothEnableDisableReason selectedReason;
	private boolean force;

	BluetoothEvent(BluetoothEnableDisableReason reason){
		selectedReason = reason;
	}

	public BluetoothEnableDisableReason getReason(){
		return selectedReason;
	}
}
