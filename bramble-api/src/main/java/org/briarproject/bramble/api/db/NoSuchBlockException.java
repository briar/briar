package org.briarproject.bramble.api.db;

/**
 * Thrown when a database operation is attempted for a block that is not in
 * the database. This exception may occur due to concurrent updates and does
 * not indicate a database error.
 */
public class NoSuchBlockException extends DbException {
}
