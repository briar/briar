package org.briarproject.bramble.api.plugin.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event asks the Bluetooth plugin to enable the Bluetooth adapter.
 */
@Immutable
@NotNullByDefault
public class EnableBluetoothEvent extends Event {
}
