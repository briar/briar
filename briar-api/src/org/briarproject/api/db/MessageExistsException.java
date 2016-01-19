package org.briarproject.api.db;

/**
 * Thrown when a duplicate message is added to the database. This exception may
 * occur due to concurrent updates and does not indicate a database error.
 */
public class MessageExistsException extends DbException {
}
