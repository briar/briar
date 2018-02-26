package org.briarproject.bramble.api.lifecycle.event;

import org.briarproject.bramble.api.event.Event;

/**
 * An event that is broadcast when the app is starting.
 * This happens after the database was opened and services were started.
 */
public class StartupEvent extends Event {
}
