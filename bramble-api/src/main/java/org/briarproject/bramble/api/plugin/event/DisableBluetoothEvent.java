package org.briarproject.bramble.api.plugin.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event that asks the Bluetooth plugin to disable the Bluetooth adapter if
 * we previously enabled it.
 */
@Immutable
@NotNullByDefault
public class DisableBluetoothEvent extends Event {
}
