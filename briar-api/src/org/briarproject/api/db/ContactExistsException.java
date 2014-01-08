package org.briarproject.api.db;

/**
 * Thrown when a duplicate contact is added to the database. This exception may
 * occur due to concurrent updates and does not indicate a database error.
 */
public class ContactExistsException extends DbException {

	private static final long serialVersionUID = -6658762011691502411L;
}
