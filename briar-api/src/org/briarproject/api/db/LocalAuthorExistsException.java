package org.briarproject.api.db;

/**
 * Thrown when a duplicate pseudonym is added to the database. This exception
 * may occur due to concurrent updates and does not indicate a database error.
 */
public class LocalAuthorExistsException extends DbException {

	private static final long serialVersionUID = -1483877298070151673L;
}
