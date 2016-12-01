package org.briarproject.bramble.api.db;

/**
 * Thrown when a duplicate contact is added to the database. This exception may
 * occur due to concurrent updates and does not indicate a database error.
 */
public class ContactExistsException extends DbException {
}
