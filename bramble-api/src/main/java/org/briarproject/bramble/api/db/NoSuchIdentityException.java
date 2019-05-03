package org.briarproject.bramble.api.db;

/**
 * Thrown when a database operation is attempted for an identity that is not in
 * the database. This exception may occur due to concurrent updates and does
 * not indicate a database error.
 */
public class NoSuchIdentityException extends DbException {
}
