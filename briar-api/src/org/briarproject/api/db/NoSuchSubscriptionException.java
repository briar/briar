package org.briarproject.api.db;

/**
 * Thrown when a database operation is attempted for a group to which the user
 * does not subscribe. This exception may occur due to concurrent updates and
 * does not indicate a database error.
 */
public class NoSuchSubscriptionException extends DbException {

	private static final long serialVersionUID = -5494178507342571697L;

}
