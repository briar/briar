package org.briarproject.bramble.api.plugin.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.plugin.BluetoothEnableDisableReason;

abstract class BluetoothEvent extends Event {
	private BluetoothEnableDisableReason selectedReason;

	BluetoothEvent(BluetoothEnableDisableReason reason){
		selectedReason = reason;
	}

	public BluetoothEnableDisableReason getReason(){
		return selectedReason;
	}
}
