package org.briarproject.bramble.api.db;

/**
 * Thrown when a multi-block message is requested from the database via a
 * method that is only suitable for requesting single-block messages.
 */
public class MessageTooLargeException extends DbException {
}
