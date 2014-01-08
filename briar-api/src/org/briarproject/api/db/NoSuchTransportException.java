package org.briarproject.api.db;

/**
 * Thrown when a database operation is attempted for a transport that is not in
 * the database. This exception may occur due to concurrent updates and does
 * not indicate a database error.
 */
public class NoSuchTransportException extends DbException {

	private static final long serialVersionUID = -6274982612759573100L;
}
