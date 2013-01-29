package net.sf.briar.api.db;

/**
 * Thrown when a database operation is attempted for a contact that is not in
 * the database. This exception may occur due to concurrent updates and does
 * not indicate a database error.
 */
public class NoSuchContactException extends DbException {

	private static final long serialVersionUID = -7048538231308207386L;
}
