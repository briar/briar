package org.briarproject.bramble.api.db;

/**
 * Thrown when a duplicate pending contact is added to the database. This
 * exception may occur due to concurrent updates and does not indicate a
 * database error.
 */
public class PendingContactExistsException extends DbException {
}
