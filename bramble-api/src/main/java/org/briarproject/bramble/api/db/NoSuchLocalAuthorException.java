package org.briarproject.bramble.api.db;

/**
 * Thrown when a database operation is attempted for a pseudonym that is not in
 * the database. This exception may occur due to concurrent updates and does
 * not indicate a database error.
 */
public class NoSuchLocalAuthorException extends DbException {
}
