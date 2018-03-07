package org.briarproject.bramble.api.plugin.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

/**
 * An event that informs the Bluetooth plugin that we have enabled the
 * Bluetooth adapter.
 */
@Immutable
@NotNullByDefault
public class BluetoothEnabledEvent extends Event {
}
