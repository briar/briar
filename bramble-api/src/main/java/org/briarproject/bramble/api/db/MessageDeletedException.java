package org.briarproject.bramble.api.db;

/**
 * Thrown when a message that has been deleted is requested from the database.
 * This exception may occur due to concurrent updates and does not indicate a
 * database error.
 */
public class MessageDeletedException extends DbException {
}
