package org.briarproject.api.db;

/**
 * Thrown when a database operation is attempted for a group that is not in the
 * database. This exception may occur due to concurrent updates and does not
 * indicate a database error.
 */
public class NoSuchGroupException extends DbException {

	private static final long serialVersionUID = -5494178507342571697L;
}
