package org.briarproject.bramble.api.db;

import org.briarproject.bramble.api.event.Event;

/**
 * An event that is broadcast before database migrations are being applied.
 */
public class DatabaseMigrationEvent extends Event {
}
