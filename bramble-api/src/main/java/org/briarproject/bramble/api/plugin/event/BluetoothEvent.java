package org.briarproject.bramble.api.plugin.event;

import org.briarproject.bramble.api.event.Event;

/**
 * to force enable and disable bluetooth
 * force enable means that the bluetooth adapter stay enabled until a force DisableBluetoothEvent is called
 * force disable stop the bluetooth adapter only when we turned it on
 */
abstract class BluetoothEvent extends Event {
	private boolean force;

	BluetoothEvent(boolean force){
		this.force = force;
	}

	public boolean isForced(){
		return force;
	}
}
