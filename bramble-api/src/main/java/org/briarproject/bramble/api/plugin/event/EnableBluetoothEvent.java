package org.briarproject.bramble.api.plugin.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event asks the Bluetooth plugin to enable the Bluetooth adapter.
 */
@Immutable
@NotNullByDefault
public class EnableBluetoothEvent extends BluetoothEvent {
	public EnableBluetoothEvent(){
		super(false);
	}

	public EnableBluetoothEvent(boolean force){
		super(force);
	}
}
